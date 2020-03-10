/*
 * Dateiname: EmptyDatasource.java
 * Projekt  : WollMux
 * Funktion : Eine Datenquelle, die keine Datensätze enthält.
 * 
 * Copyright (c) 2008-2016 Landeshauptstadt München
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
 * 28.10.2005 | BNK | Erstellung
 * 28.10.2005 | BNK | +getName()
 * 31.10.2005 | BNK | +find()
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

import de.muenchen.allg.itd51.wollmux.core.db.OOoDatasource.OOoDataset;

/**
 * Eine Dummy-Datenquelle, die im Schema keine Datensätze enthält und als QueryResult
 * bei getDatasetsByKey den String "&lt;key&gt;" zurück liefert.
 * 
 * verwendet im  noConfig Modus.
 * 
 * @author Matthias Benkmann (D-III-ITD 5.1)
 */
public class DummyDatasourceWithMessagebox implements Datasource<OOoDataset>
{
  private static List<OOoDataset> emptyResults = new ArrayList<>();

  private List<String> schema;

  private String name;

  public DummyDatasourceWithMessagebox(List<String> schema, String name)
  {
    this.schema = schema;
    this.name = name;
  }

  @Override
  public List<String> getSchema()
  {
    return null;
  }

  @Override
  public List<OOoDataset> getDatasetsByKey(Collection<String> keys)
  {
    return emptyResults;
  }

  @Override
  public List<OOoDataset> find(List<QueryPart> query)
  {
    return emptyResults;
  }

  @Override
  public List<OOoDataset> getContents()
  {
    return emptyResults;
  }

  @Override
  public String getName()
  {
    return "";
  }

}
