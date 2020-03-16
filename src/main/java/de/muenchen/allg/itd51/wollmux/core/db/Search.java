/*
 * Dateiname: Search.java
 * Projekt  : WollMux
 * Funktion : Stellt Methoden zum Suchen in Datenquellen zur Verfügung
 * 
 * Copyright (c) 2010-2015 Landeshauptstadt München
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the European Union Public Licence (EUPL), 
 * version 1.0 (or any later version).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * European Union Public Licence for more details.
 *
 * You should have received a copy of the European Union Public Licence
 * along with this program. If not, see 
 * http://ec.europa.eu/idabc/en/document/7330
 *
 * Änderungshistorie:
 * Datum      | Wer | Änderungsgrund
 * -------------------------------------------------------------------
 * 18.03.2010 | BED | Erstellung
 * 11.11.2010 | BED | Leerstring als Sucheingabe wieder möglich
 * -------------------------------------------------------------------
 *
 * @author Daniel Benkmann (D-III-ITD-D101)
 * 
 */
package de.muenchen.allg.itd51.wollmux.core.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.muenchen.allg.itd51.wollmux.core.parser.ConfigThingy;

/**
 * Diese Klasse stellt Methoden zur Verfügung um in Datenquellen Suchen durchzuführen.
 */
public class Search
{
  private static final Logger LOGGER = LoggerFactory.getLogger(Search.class);

  private Search()
  {
    // hide implicit public constructor
  }

  /**
   * Führt die übergebene Suchanfrage gemäß der übergebenen Suchstrategie aus und liefert die
   * Ergebnisse in einem {@link QueryResults}-Objekt zurück. Falls einer der übergebenen Parameter
   * <code>null</code> ist oder falls der queryString leer ist, wird <code>null</code>
   * zurückgeliefert.
   * 
   * @param queryString
   *          die Suchanfrage
   * @param searchStrategy
   *          die zu verwendende Suchstrategie
   * @param dj
   *          die virtuelle Datenbank (siehe {@link DatasourceJoiner}), in der gesucht werden soll
   * @param useDjMainDatasource
   *          gibt an, ob unabhängig von den eventuell in der Suchstrategie festgelegten
   *          Datenquellen auf jeden Fall immer in der Hauptdatenquelle von dj gesucht werden soll.
   *          Wenn hier <code>true</code> übergeben wird, enthalten die als Ergebnis der Suche
   *          zurückgelieferten QueryResults auf jeden Fall {@link DJDataset}s.
   * @throws IllegalArgumentException
   *           falls eine Datenquelle, in der gesucht werden soll, nicht existiert
   * @return Results as an Iterable of Dataset as {@link QueryResults}
   */
  public static List<Dataset> search(String queryString, SearchStrategy searchStrategy,
      DatasourceJoiner dj, boolean useDjMainDatasource)
  {
    if (queryString == null || searchStrategy == null || dj == null)
    {
      return new ArrayList<>();
    }

    List<Query> queries = parseQuery(searchStrategy, queryString);

    List<Dataset> results = null;
    List<List<Dataset>> listOfQueryResultsList = new ArrayList<>();

    for (Query query : queries)
    {
      if (query.numberOfQueryParts() == 0)
      {
        results = (useDjMainDatasource ? dj.getContentsOfMainDatasource()
            : dj.getContentsOf(query.getDatasourceName()));
      } else
      {
        results = (useDjMainDatasource ? dj.find(query.getQueryParts()) : dj.find(query));
      }
      listOfQueryResultsList.add(results);
    }

    return mergeListOfQueryResultsList(listOfQueryResultsList);
  }

  /**
   * Führt die übergebene Suchanfrage gemäß der übergebenen Suchstrategie aus und liefert die
   * Ergebnisse in einem {@link QueryResults}-Objekt zurück. Falls einer der übergebenen Parameter
   * <code>null</code> ist oder falls der queryString leer ist, wird <code>null</code>
   * zurückgeliefert.
   * 
   * @param query
   *          die Suchanfrage
   * @param dj
   *          die virtuelle Datenbank (siehe {@link DatasourceJoiner}), in der gesucht werden soll
   * @return Results as an Iterable of Dataset as {@link QueryResults}
   */
  public static List<Dataset> search(Map<String, String> query, DatasourceJoiner dj)
  {
    List<QueryPart> parts = new ArrayList<>();

    for (String key : query.keySet())
    {
      QueryPart qp = new QueryPart(key, query.get(key));
      parts.add(qp);
    }

    return dj.find(parts);
  }

  /**
   * Compares two given datasets. If one of the values within a dataset is different from the second
   * dataset, true will be returned.
   * 
   * @param dataset
   *          First dataset.
   * @param ldapDataset
   *          A second dataset to compare with.
   * @param dj
   *          An instance of the {@link DatasourceJoiner}
   * @return Returns true if different values were detected.
   */
  public static boolean hasLDAPDataChanged(Dataset dataset, Dataset ldapDataset,
      DatasourceJoiner dj)
  {
    boolean hasChanged = false;

    if (dj == null || dataset == null || ldapDataset == null)
      return hasChanged;

    for (String columnName : dj.getMainDatasourceSchema())
    {
      try
      {
        String ldapDSValue = ldapDataset.get(columnName);
        String datasetValue = dataset.get(columnName);

        if ((ldapDSValue == null && datasetValue != null && !datasetValue.isEmpty())
            || (ldapDSValue != null && datasetValue != null && !ldapDSValue.equals(datasetValue)))
        {
          hasChanged = true;
          break;
        }
      } catch (ColumnNotFoundException e)
      {
        LOGGER.error("", e);
      }
    }

    return hasChanged;
  }

  /**
   * Führt die Ergenismengen zusammen. Dabei werden mehrfache Ergebnisse ausgefiltert.
   * 
   * @return bereinigte Ergebnisliste.
   */
  private static List<Dataset> mergeListOfQueryResultsList(
      List<List<Dataset>> listOfQueryResultsList)
  {
    List<Dataset> mergedLists = new ArrayList<>();

    for (List<Dataset> queryResults : listOfQueryResultsList)
    {
      mergedLists.addAll(queryResults);
    }

    return mergedLists;
  }

  /**
   * Liefert zur Anfrage queryString eine Liste von {@link Query}s, die der Reihe nach probiert
   * werden sollten, gemäß der Suchstrategie searchStrategy (siehe
   * {@link SearchStrategy#parse(ConfigThingy)}). Gibt es für die übergebene Anzahl Wörter keine
   * Suchstrategie, so wird solange das letzte Wort entfernt bis entweder nichts mehr übrig ist oder
   * eine Suchstrategie für die Anzahl Wörter gefunden wurde.
   * 
   * @return die leere Liste falls keine Liste bestimmt werden konnte.
   */
  private static List<Query> parseQuery(SearchStrategy searchStrategy, String queryString)
  {
    List<Query> queryList = new ArrayList<>();

    // Kommata durch Space ersetzen (d.h. "Benkmann,Matthias" -> "Benkmann
    // Matthias")
    queryString = queryString.replaceAll(",", " ");

    // Suchstring zerlegen.
    Stream<String> queryStream = Arrays.stream(queryString.trim().split("\\p{Space}+"));
    // Formatieren und leere Wörter entfernen
    String[] queryArray = queryStream.map(Search::formatQuery).filter(query -> query.length() != 0)
        .toArray(String[]::new);

    int count = queryArray.length;

    // Passende Suchstrategie finden; falls nötig dazu Wörter am Ende weglassen.
    while (count >= 0 && searchStrategy.getTemplate(count) == null)
      --count;

    // keine Suchstrategie gefunden
    if (count < 0)
    {
      return queryList;
    }

    List<Query> templateList = searchStrategy.getTemplate(count);
    for (Query template : templateList)
    {
      queryList.add(resolveTemplate(template, queryArray, count));
    }

    return queryList;
  }

  /**
   * Benutzerseitig wir nur ein einzelnes Sternchen am Ende eines Wortes akzeptiert. Deswegen
   * entferne alle anderen Sternchen. Ein Punkt am Ende eines Wortes wird als Abkürzung
   * interpretiert und durch Sternchen ersetzt.
   */
  private static String formatQuery(String query)
  {
    boolean suffixStar = query.endsWith("*") || query.endsWith(".");
    String modifiedQuery = query;
    if (query.endsWith("."))
    {
      modifiedQuery = query.substring(0, query.length() - 1);
    }
    modifiedQuery = modifiedQuery.replaceAll("\\*", "");
    if (suffixStar && query.length() != 0)
    {
      modifiedQuery += "*";
    }
    return modifiedQuery;
  }

  /**
   * Nimmt ein Template für eine Suchanfrage entgegen (das Variablen der Form "${suchanfrageX}"
   * enthalten kann) und instanziiert es mit Wörtern aus words, wobei nur die ersten wordcount
   * Einträge von words beachtet werden.
   */
  private static Query resolveTemplate(Query template, String[] words, int wordcount)
  {
    String dbName = template.getDatasourceName();
    List<QueryPart> listOfQueryParts = new ArrayList<>();
    Iterator<QueryPart> qpIter = template.iterator();
    while (qpIter.hasNext())
    {
      QueryPart templatePart = qpIter.next();
      String str = templatePart.getSearchString();

      for (int i = 0; i < wordcount; ++i)
      {
        str = str.replaceAll("\\$\\{suchanfrage" + (i + 1) + "\\}",
            words[i].replaceAll("\\$", "\\\\\\$"));
      }

      QueryPart part = new QueryPart(templatePart.getColumnName(), str);
      listOfQueryParts.add(part);
    }
    return new Query(dbName, listOfQueryParts);
  }

}
