/*
 * Dateiname: RAMDatasource.java
 * Projekt  : WollMux
 * Funktion : Oberklasse für Datasources, die ihre Daten vollständig
 *            im Speicher halten
 * 
 * Copyright (c) 2008-2015 Landeshauptstadt München
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
 * 31.10.2005 | BNK | Erstellung
 * 03.11.2005 | BNK | besser kommentiert
 * 10.11.2005 | BNK | Refactoring: DatasetChecker & Co. in eigene Klassen
 * -------------------------------------------------------------------
 *
 * @author Matthias Benkmann (D-III-ITD 5.1)
 * @version 1.0
 * 
 */
package de.muenchen.allg.itd51.wollmux.core.db;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * Oberklasse für Datasources, die ihre Daten vollständig im Speicher halten
 */
public class RAMDatasource implements Datasource<Dataset>
{
  /**
   * Das Schema dieser Datenquelle.
   */
  private List<String> schema;

  /**
   * Liste aller Datasets, die in dieser Datasource gespeichert sind.
   */
  private List<Dataset> data;

  /**
   * Der Name dieser Datenquelle.
   */
  private String name;

  /**
   * Erzeugt eine neue RAMDatasource mit Namen name. data und schema werden direkt als Referenz
   * eingebunden, nicht kopiert.
   * 
   * @param name
   *          der Name der Datenquelle
   * @param schema
   *          das Schema der Datenquelle
   * @param data
   *          die Datensätze der Datenquelle
   */
  public RAMDatasource(String name, List<String> schema, List<Dataset> data)
  {
    init(name, schema, data);
  }

  /**
   * Erzeugt eine uninitialisierte RAMDatasource. Eine abgeleitete Klasse, die diesen Konstruktor
   * verwendet sollte init() aufrufen, um die nötigen Initialisierungen zu erledigen.
   */
  protected RAMDatasource()
  {
  }

  /**
   * Führt die Initialisierungsaktionen des Konstruktors mit den gleichen Parametern aus. Diese
   * Methode sollte von abgeleiteten Klassen verwendet werden, wenn sie den Konstruktor ohne
   * Argumente verwenden.
   */
  protected void init(String name, List<String> schema, List<Dataset> data)
  {
    this.schema = schema;
    this.data = data;
    this.name = name;
  }

  @Override
  public List<String> getSchema()
  {
    return new ArrayList<>(schema);
  }

  @Override
  public List<Dataset> getDatasetsByKey(Collection<String> keys)
  {
    List<Dataset> res = new ArrayList<>();

    for (Dataset ds : data)
    {
      if (keys.contains(ds.getKey()))
      {
        res.add(ds);
      }
    }

    return res;
  }

  /*
   * (non-Javadoc)
   * 
   * @see de.muenchen.allg.itd51.wollmux.db.Datasource#find(java.util.List, long)
   */
  @Override
  public List<Dataset> find(List<QueryPart> query)
  {
    if (query.isEmpty())
    {
      return new ArrayList<>();
    }

    Predicate<Dataset> pred = DatasetPredicate.makePredicate(query);

    List<Dataset> results = new ArrayList<>();

    data.forEach(ds -> {
      if (pred.test(ds))
      {
        results.add(ds);
      }
    });

    return results;
  }

  @Override
  public List<Dataset> getContents()
  {
    return new ArrayList<>(data);
  }

  /*
   * (non-Javadoc)
   * 
   * @see de.muenchen.allg.itd51.wollmux.db.Datasource#getName()
   */
  @Override
  public String getName()
  {
    return name;
  }

}
