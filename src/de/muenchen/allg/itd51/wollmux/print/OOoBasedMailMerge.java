/*
 * Dateiname: OOoBasedMailMerge.java
 * Projekt  : WollMux
 * Funktion : Seriendruck über den OOo MailMergeService
 *
 * Copyright (c) 2011-2019 Landeshauptstadt München
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
 * 15.06.2011 | LUT | Erstellung
 * 12.07.2013 | JGM | Anpassungen an die neue UNO API zum setzten des Druckers
 * -------------------------------------------------------------------
 *
 * @author Christoph Lutz (D-III-ITD-D101)
 *
 */
package de.muenchen.allg.itd51.wollmux.print;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.star.beans.NamedValue;
import com.sun.star.beans.PropertyValue;
import com.sun.star.beans.PropertyVetoException;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.beans.XPropertySet;
import com.sun.star.container.NoSuchElementException;
import com.sun.star.container.XEnumeration;
import com.sun.star.container.XNameAccess;
import com.sun.star.frame.XModel;
import com.sun.star.frame.XStorable;
import com.sun.star.io.IOException;
import com.sun.star.lang.IllegalArgumentException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XMultiServiceFactory;
import com.sun.star.lang.XSingleServiceFactory;
import com.sun.star.sdb.CommandType;
import com.sun.star.sdb.XDocumentDataSource;
import com.sun.star.task.XJob;
import com.sun.star.text.MailMergeEvent;
import com.sun.star.text.MailMergeType;
import com.sun.star.text.XDependentTextField;
import com.sun.star.text.XMailMergeBroadcaster;
import com.sun.star.text.XMailMergeListener;
import com.sun.star.text.XTextContent;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextRange;
import com.sun.star.text.XTextSection;
import com.sun.star.text.XTextSectionsSupplier;
import com.sun.star.uno.AnyConverter;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XNamingService;
import com.sun.star.util.CloseVetoException;
import com.sun.star.util.URL;
import com.sun.star.util.XCancellable;
import com.sun.star.util.XChangesBatch;

import de.muenchen.allg.afid.UNO;
import de.muenchen.allg.afid.UnoProps;
import de.muenchen.allg.itd51.wollmux.SachleitendeVerfuegung;
import de.muenchen.allg.itd51.wollmux.XPrintModel;
import de.muenchen.allg.itd51.wollmux.core.document.FormFieldFactory;
import de.muenchen.allg.itd51.wollmux.core.document.FormFieldFactory.FormField;
import de.muenchen.allg.itd51.wollmux.core.document.FormFieldFactory.FormFieldType;
import de.muenchen.allg.itd51.wollmux.core.document.PersistentDataContainer;
import de.muenchen.allg.itd51.wollmux.core.document.PersistentDataContainer.DataID;
import de.muenchen.allg.itd51.wollmux.core.document.SimulationResults;
import de.muenchen.allg.itd51.wollmux.core.document.SimulationResults.SimulationResultsProcessor;
import de.muenchen.allg.itd51.wollmux.core.document.TextDocumentModel;
import de.muenchen.allg.itd51.wollmux.core.document.commands.DocumentCommand;
import de.muenchen.allg.itd51.wollmux.core.document.commands.DocumentCommand.InsertFormValue;
import de.muenchen.allg.itd51.wollmux.core.document.commands.DocumentCommands;
import de.muenchen.allg.itd51.wollmux.core.util.L;
import de.muenchen.allg.itd51.wollmux.core.util.Utils;
import de.muenchen.allg.itd51.wollmux.dialog.InfoDialog;
import de.muenchen.allg.itd51.wollmux.dialog.mailmerge.MailMergeNew;
import de.muenchen.allg.itd51.wollmux.document.DocumentManager;

public class OOoBasedMailMerge
{

  private static final Logger LOGGER = LoggerFactory
      .getLogger(OOoBasedMailMerge.class);

  private static final String SEP = ":";

  private static final String COLUMN_PREFIX_SINGLE_PARAMETER_FUNCTION = "WM:SP";

  private static final String COLUMN_PREFIX_CHECKBOX_FUNCTION = "WM:CB";

  private static final String COLUMN_PREFIX_MULTI_PARAMETER_FUNCTION = "WM:MP";

  private static final String COLUMN_PREFIX_TEXTSECTION = "WM:SE_";

  private static final String TEMP_WOLLMUX_MAILMERGE_PREFIX = "WollMuxMailMerge";

  private static final String DATASOURCE_ODB_FILENAME = "datasource.odb";

  private static final String TABLE_NAME = "data";

  private static final char OPENSYMBOL_CHECKED = 0xE4C4;

  private static final char OPENSYMBOL_UNCHECKED = 0xE470;

  /**
   * Druckfunktion für den Seriendruck in ein Gesamtdokument mit Hilfe des
   * Office-Seriendrucks.
   * 
   * @author Christoph Lutz (D-III-ITD 5.1)
   */
  public static void oooMailMerge(final XPrintModel pmod, OutputType type)
  {

    PrintModels.setStage(pmod, L.m("Seriendruck vorbereiten"));

    // prüfe ob OutputType.toShell von der Office-Version
    // unterstützt wird. Falls nicht, falle zurück auf OutputType.toFile.
    if (type == OutputType.toShell && type.getUNOMailMergeType() == null)
    {
      LOGGER.debug(L.m("Die aktuelle Office-Version unterstützt MailMergeType.SHELL nicht. Verwende MailMergeType.FILE stattdessen"));
      type = OutputType.toFile;
    }

    File tmpDir = createMailMergeTempdir();

    // Datenquelle mit über mailMergeNewSetFormValue simulierten Daten erstellen
    OOoDataSource ds = new CsvBasedOOoDataSource(tmpDir);
    try
    {
      MailMergeNew.mailMergeNewSetFormValue(pmod, ds);
      if (pmod.isCanceled()) return;
      ds.getDataSourceWriter().flushAndClose();
    }
    catch (Exception e)
    {
      LOGGER.error(
        L.m("OOo-Based-MailMerge: kann Simulationsdatenquelle nicht erzeugen!"), e);
      return;
    }
    if (ds.getSize() == 0)
    {
      InfoDialog.showInfoModal(
        L.m("WollMux-Seriendruck"),
        L.m("Der Seriendruck wurde abgebrochen, da Ihr Druckauftrag keine Datensätze enthält."));
      pmod.cancel();
      return;
    }

    XDocumentDataSource dataSource = ds.createXDocumentDatasource();

    String dbName = registerTempDatasouce(dataSource);

    boolean loadPrintSettings = false;
    try
    {
      loadPrintSettings = modifyLoadPrinterSetting(true);
    } catch (Exception e1)
    {
      LOGGER.warn(
          "Die Option 'Laden von Druckeinstellungen mit dem Dokument' konnte nicht gesetzt werden.\n"
              + "Seriendrucke auf einem Drucker haben eventuell falsche Optionen gesetzt.");
      LOGGER.debug("", e1);
    }
    File inputFile = createAndAdjustInputFile(tmpDir, pmod.getTextDocument(), dbName);

    LOGGER.debug(L.m("Temporäre Datenquelle: %1", dbName));
    if (pmod.isCanceled()) {
      return;
    }

    MailMergeThread t = null;
    try
    {
      PrintModels.setStage(pmod, L.m("Gesamtdokument erzeugen"));
      ProgressUpdater updater =
        new ProgressUpdater(pmod, (int) Math.ceil((double) ds.getSize()
          / countNextSets(pmod.getTextDocument())));

      t = runMailMerge(dbName, tmpDir, inputFile, updater, type);
    }
    catch (Exception e)
    {
      LOGGER.error(L.m("Fehler beim Starten des OOo-Seriendrucks"), e);
    }

    // Warte auf Ende des MailMerge-Threads unter Berücksichtigung von
    // pmod.isCanceled()
    while (t != null && t.isAlive())
      try
      {
        t.join(1000);
        if (pmod.isCanceled())
        {
          t.cancel();
          break;
        }
      }
      catch (InterruptedException e)
      {}
    if (pmod.isCanceled() && t.isAlive())
    {
      t.interrupt();
      LOGGER.debug(L.m("Der OOo-Seriendruck wurde abgebrochen"));
      // aber aufräumen tun wir noch...
    }

    removeTempDatasource(dbName, tmpDir);
    ds.remove();
    inputFile.delete();
    try
    {
      modifyLoadPrinterSetting(loadPrintSettings);
    } catch (Exception e1)
    {
      LOGGER.warn(
          "Die Einstellung 'Laden von Druckeinstellungen mit dem Dokument' konnte nicht zurückgesetzt werden.");
      LOGGER.debug("", e1);
    }

    // ... jetzt können wir nach Benutzerabbruch aufhören
    if (pmod.isCanceled()) return;

    if (type == OutputType.toFile)
    {
      // Output-File als Template öffnen und aufräumen
      File outputFile = new File(tmpDir, "output0.odt");
      if (outputFile.exists())
        try
        {
          String unoURL =
            UNO.getParsedUNOUrl(outputFile.toURI().toString()).Complete;
          LOGGER.debug(L.m("Öffne erzeugtes Gesamtdokument %1", unoURL));
          UNO.loadComponentFromURL(unoURL, true, false);
        }
        catch (Exception e)
        {
          LOGGER.error("", e);
        }
      else
      {
        InfoDialog.showInfoModal(L.m("WollMux-Seriendruck"),
            L.m("Leider konnte kein Gesamtdokument (ODT) erstellt werden."));
        pmod.cancel();
      }
      outputFile.delete();
    }

    else if (type == OutputType.toShell)
    {
      XTextDocument result = UNO.XTextDocument(t.getResult());
      try
      {
        pmod.setPropertyValue(PrintFunction.PROP_PRINT_RESULT, result);
        pmod.printWithProps();
      } catch (UnknownPropertyException | PropertyVetoException | IllegalArgumentException | WrappedTargetException e)
      {
        LOGGER.error("", e);
        InfoDialog.showInfoModal(L.m("WollMux-Seriendruck"),
            L.m("Leider konnte der Druck nicht abgeschlossen werden."));
        pmod.cancel();
      }
    }

    tmpDir.delete();
  }

  /**
   * A optional XCancellable mail merge thread.
   *
   * @author Jan-Marek Glogowski (ITM-I23)
   */
  private static class MailMergeThread extends Thread
  {
    private XCancellable mailMergeCancellable = null;

    private Object result = null;

    private final XJob mailMerge;

    private final File outputDir;

    private final ArrayList<NamedValue> mmProps;

    MailMergeThread(XJob mailMerge, File outputDir, ArrayList<NamedValue> mmProps)
    {
      this.mailMerge = mailMerge;
      this.outputDir = outputDir;
      this.mmProps = mmProps;
    }

    @Override
    public void run()
    {
      try
      {
        LOGGER.debug(L.m("Starting OOo-MailMerge in Verzeichnis %1", outputDir));
        // The XCancellable mail merge interface was included in LO >= 4.3.
        mailMergeCancellable =
          UnoRuntime.queryInterface(XCancellable.class, mailMerge);
        if (mailMergeCancellable != null)
          LOGGER.debug(L.m("XCancellable interface im mailMerge-Objekt gefunden!"));
        else
          LOGGER.debug(L.m("KEIN XCancellable interface im mailMerge-Objekt gefunden!"));

        result = mailMerge.execute(mmProps.toArray(new NamedValue[mmProps.size()]));

        LOGGER.debug(L.m("Finished Mail Merge"));
      }
      catch (Exception e)
      {
        LOGGER.debug(L.m("OOo-MailMergeService fehlgeschlagen: %1", e.getMessage()));
      }
      mailMergeCancellable = null;
    }

    public synchronized void cancel()
    {
      if (mailMergeCancellable != null) mailMergeCancellable.cancel();
    }

    public Object getResult()
    {
      return result;
    }
  }

  /**
   * Übernimmt das Aktualisieren der Fortschrittsanzeige im XPrintModel pmod.
   *
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  private static class ProgressUpdater
  {
    private XPrintModel pmod;

    private int currentCount;

    public final int maxDatasets;

    public ProgressUpdater(XPrintModel pmod, int maxDatasets)
    {
      this.pmod = pmod;
      this.currentCount = 0;
      this.maxDatasets = maxDatasets;
      pmod.setPrintProgressMaxValue((short) maxDatasets);
      pmod.setPrintProgressValue((short) 0);
    }

    public void incrementProgress()
    {
      pmod.setPrintProgressValue((short) ++currentCount);
    }

    public void setMessage(String text)
    {
      this.currentCount = 0;
      pmod.setPrintMessage(text);
    }
  }

  /**
   * Repräsentiert eine (noch nicht registrierte) Datenquelle für Office.
   *
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  public static abstract class OOoDataSource implements SimulationResultsProcessor
  {
    /**
     * Liefert das für die Registrierung der OOo-Datenquelle benötigte
     * {@link XDocumentDataSource}-Objekt zurück.
     * 
     * @author Christoph Lutz (D-III-ITD-D101)
     */
    abstract public XDocumentDataSource createXDocumentDatasource();

    /**
     * Liefert einen {@link DataSourceWriter} zurück, über den Datensätze in die
     * Datenquelle geschrieben werden können.
     * 
     * @author Christoph Lutz (D-III-ITD-D101)
     */
    abstract public DataSourceWriter getDataSourceWriter();

    /**
     * Liefert die Anzahl der Datensätze der Datenquelle zurück.
     * 
     * @author Christoph Lutz (D-III-ITD-D101)
     */
    abstract public int getSize();

    /**
     * Entfernt die Datenquelle
     * 
     * @author Christoph Lutz (D-III-ITD-D101)
     */
    abstract public void remove();

    /*
     * (non-Javadoc)
     *
     * @see
     * de.muenchen.allg.itd51.wollmux.SimulationResults.SimulationResultsProcessor
     * #processSimulationResults(de.muenchen.allg.itd51.wollmux.SimulationResults)
     */
    @Override
    public void processSimulationResults(SimulationResults simRes)
    {
      if (simRes == null) return;

      HashMap<String, String> data =
        new HashMap<>(simRes.getFormFieldValues());
      for (FormField field : simRes.getFormFields())
      {
        String columnName = getSpecialColumnNameForFormField(field);
        if (columnName == null) continue;
        String content = simRes.getFormFieldContent(field);

        // Checkboxen müssen über bestimmte Zeichen der Schriftart OpenSymbol
        // angenähert werden.
        if (field.getType() == FormFieldType.CHECKBOX_FORM_FIELD)
          if (content.equalsIgnoreCase("TRUE"))
            content = "" + OPENSYMBOL_CHECKED;
          else
            content = "" + OPENSYMBOL_UNCHECKED;

        data.put(columnName, content);
      }

      for (Map.Entry<String, Boolean> entry : simRes.getGroupsVisibilityState().entrySet())
      {
        LOGGER.info(entry.getKey() + " --> " + entry.getValue());
        data.put(COLUMN_PREFIX_TEXTSECTION + entry.getKey(), entry.getValue().toString());
      }

      try
      {
        getDataSourceWriter().addDataset(data);
      }
      catch (Exception e)
      {
        LOGGER.error("", e);
      }
    }
  }

  /**
   * Implementierung einer {@link OOoDataSource}, die als Backend ein CSV-Datei
   * verwendet.
   *
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  public static class CsvBasedOOoDataSource extends OOoDataSource
  {
    File parentDir;

    CSVDataSourceWriter dsw;

    /**
     * Erzeugt eine {@link OOoDataSource}, die als Backend eine CSV-Datei verwendet
     * und die dafür notwendige Datei (eine .csv-Datei) im Verzeichnis parentDir
     * ablegt.
     */
    public CsvBasedOOoDataSource(File parentDir)
    {
      this.parentDir = parentDir;
      this.dsw = new CSVDataSourceWriter(parentDir);
    }

    /*
     * (non-Javadoc)
     *
     * @seede.muenchen.allg.itd51.wollmux.func.OOoBasedMailMerge.OOoDataSource#
     * getDataSourceWriter()
     */
    @Override
    public DataSourceWriter getDataSourceWriter()
    {
      return dsw;
    }

    /*
     * (non-Javadoc)
     *
     * @seede.muenchen.allg.itd51.wollmux.func.OOoBasedMailMerge.OOoDataSource#
     * createXDocumentDatasource()
     */
    @Override
    public XDocumentDataSource createXDocumentDatasource()
    {
      XSingleServiceFactory dbContext =
        UNO.XSingleServiceFactory(UNO.createUNOService("com.sun.star.sdb.DatabaseContext"));
      XDocumentDataSource dataSource = null;
      if (dbContext != null)
      {
        try
        {
          dataSource = UNO.XDocumentDataSource(dbContext.createInstance());
        } catch (Exception e)
        {
          LOGGER.error("", e);
        }
      }

      if (dataSource != null)
      {
        String dirURL = UNO.getParsedUNOUrl(parentDir.toURI().toString()).Complete;
        Utils.setProperty(dataSource, "URL", "sdbc:flat:" + dirURL);

        UnoProps p = new UnoProps();
        p.setPropertyValue("Extension", "csv");
        p.setPropertyValue("CharSet", "UTF-8");
        p.setPropertyValue("FixedLength", false);
        p.setPropertyValue("HeaderLine", true);
        p.setPropertyValue("FieldDelimiter", ",");
        p.setPropertyValue("StringDelimiter", "\"");
        p.setPropertyValue("DecimalDelimiter", ".");
        p.setPropertyValue("ThousandDelimiter", "");
        Utils.setProperty(dataSource, "Info", p.getProps());

        XStorable xStorable = UNO.XStorable(dataSource.getDatabaseDocument());
        XModel model = UNO.XModel(xStorable);
        URL url = null;
        File tmpFile = new File(parentDir, DATASOURCE_ODB_FILENAME);
        url = UNO.getParsedUNOUrl(tmpFile.toURI().toString());
        if (url != null && xStorable != null && model != null)
        {
          try
          {
            xStorable.storeAsURL(url.Complete, model.getArgs());
          } catch (IOException e)
          {
            LOGGER.error("", e);
          }
        }
      }
      return dataSource;

    }

    /*
     * (non-Javadoc)
     *
     * @see
     * de.muenchen.allg.itd51.wollmux.func.OOoBasedMailMerge.OOoDataSource#getSize()
     */
    @Override
    public int getSize()
    {
      return dsw.getSize();
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * de.muenchen.allg.itd51.wollmux.func.OOoBasedMailMerge.OOoDataSource#remove()
     */
    @Override
    public void remove()
    {
      dsw.getCSVFile().delete();
    }
  }

  /**
   * Beschreibt einen DataSourceWriter mit dem die Daten des Seriendrucks in eine
   * Datenquelle geschrieben werden können. Eine konkrete Ableitungen ist der
   * {@link CSVDataSourceWriter}.
   *
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  public static interface DataSourceWriter
  {
    /**
     * Fügt der zu erzeugenden Datenquelle einen neuen Datensatz hinzu durch
     * Schlüssel/Wert-Paare in einer HashMap definiert ist.
     *
     * @throws Exception
     *           falls etwas beim Hinzufügen schief geht.
     * 
     * @author Christoph Lutz (D-III-ITD-D101)
     */
    public void addDataset(HashMap<String, String> ds) throws Exception;

    /**
     * Nachdem mit {@link #addDataset(HashMap)} alle Datensätze hinzugefügt wurden
     * schließt der Aufruf dieser Methode die Erzeugung der Datenquelle ab. Nach dem
     * Aufruf von {@link #flushAndClose()} ist die Erzeugung abgeschlossen und es
     * darf kein weiterer Aufruf von {@link #addDataset(HashMap)} erfolgen (bzw.
     * dieser ist dann ohne Wirkung).
     *
     * @throws Exception
     *           falls etwas beim Finalisieren schief geht.
     * 
     * @author Christoph Lutz (D-III-ITD-D101)
     */
    public void flushAndClose() throws Exception;

    /**
     * Liefert die Anzahl der (bisher) mit {@link #addDataset(HashMap)} hinzugefügten
     * Datensätze zurück.
     * 
     * @author Christoph Lutz (D-III-ITD-D101)
     */
    public int getSize();
  }

  /**
   * Implementiert einen DataSourceWriter, der Daten in eine CSV-Datei data.csv in
   * einem frei wählbaren Zielverzeichnis schreibt.
   *
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  public static class CSVDataSourceWriter implements DataSourceWriter
  {
    /**
     * Enthält die zu erzeugende bzw. erzeugte csv-Datei.
     */
    File csvFile = null;

    /**
     * Sammelt alle über {@link #addDataset(HashMap)} gesetzten Datensätze
     */
    ArrayList<HashMap<String, String>> datasets;

    /**
     * Sammelt die Namen aller über {@link #addDataset(HashMap)} gesetzten Spalten.
     */
    HashSet<String> columns;

    /**
     * Enthält nach einem Aufruf von {@link #getHeaders()} die sortierten Headers.
     */
    ArrayList<String> headers = null;

    /**
     * Erzeugt einen CSVDataSourceWriter, der die zu erzeugende csv-Datei in
     * parentDir ablegt.
     */
    public CSVDataSourceWriter(File parentDir)
    {
      csvFile = new File(parentDir, TABLE_NAME + ".csv");
      datasets = new ArrayList<>();
      columns = new HashSet<>();
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * de.muenchen.allg.itd51.wollmux.func.OOoBasedMailMerge.DataSourceWriter#getSize
     * ()
     */
    @Override
    public int getSize()
    {
      return datasets.size();
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * de.muenchen.allg.itd51.wollmux.func.OOoBasedMailMerge.DataSourceWriter#addDataset
     * (java.util.HashMap)
     */
    @Override
    public void addDataset(HashMap<String, String> ds) throws Exception
    {
      datasets.add(ds);
      columns.addAll(ds.keySet());
    }

    /*
     * (non-Javadoc)
     *
     * @seede.muenchen.allg.itd51.wollmux.func.OOoBasedMailMerge.DataSourceWriter#
     * flushAndClose()
     */
    @Override
    public void flushAndClose() throws Exception
    {
      FileOutputStream fos = new FileOutputStream(csvFile);
      PrintStream p = new PrintStream(fos, true, "UTF-8");
      p.print(line(getHeaders()));
      for (HashMap<String, String> ds : datasets)
      {
        ArrayList<String> entries = new ArrayList<>();
        for (String key : getHeaders())
        {
          String val = ds.get(key);
          if (val == null) val = "";
          entries.add(val);
        }
        p.print(line(entries));
      }
      p.close();
    }

    /**
     * Erzeugt die zu dem durch list repräsentierten Datensatz zugehörige
     * vollständige Textzeile für die csv-Datei.
     * 
     * @author Christoph Lutz (D-III-ITD-D101)
     */
    private String line(List<String> list)
    {
      StringBuffer buf = new StringBuffer();
      for (String el : list)
      {
        if (buf.length() != 0) buf.append(",");
        buf.append(literal(el));
      }
      buf.append("\n");
      return buf.toString();
    }

    /**
     * Erzeugt ein für die csv-Datei gültiges literal aus dem Wert value und
     * übernimmt insbesondere das Escaping der Anführungszeichen.
     *
     * @author Christoph Lutz (D-III-ITD-D101)
     */
    private String literal(String value)
    {
      String esc = value.replaceAll("\"", "\"\"");
      return "\"" + esc + "\"";
    }

    /**
     * Liefert eine alphabetisch sortierte Liste alle Spaltennamen zurück, die jemals
     * über {@link #addDataset(HashMap)} benutzt bzw. gesetzt wurden.
     * 
     * @author Christoph Lutz (D-III-ITD-D101)
     */
    private ArrayList<String> getHeaders()
    {
      if (headers != null) return headers;
      headers = new ArrayList<>(columns);
      Collections.sort(headers);
      return headers;
    }

    /**
     * Liefert das File-Objekt der csv-Datei zurück, in die geschrieben wird/wurde.
     * 
     * @author Christoph Lutz (D-III-ITD-D101)
     */
    public File getCSVFile()
    {
      return csvFile;
    }
  }

  /**
   * Modifies the configuration option for loading print properties when files are opened.
   *
   * @param load
   *          True if the properties should be loaded, false otherwise. @return The value before the
   *          modification.
   * @throws com.sun.star.uno.Exception
   *           Couldn't modify the property.
   */
  private static Boolean modifyLoadPrinterSetting(boolean load) throws com.sun.star.uno.Exception
  {
    Object cp = UNO.createUNOService("com.sun.star.configuration.ConfigurationProvider");
    com.sun.star.beans.PropertyValue aPathArgument = new com.sun.star.beans.PropertyValue();
    aPathArgument.Name = "nodepath";
    aPathArgument.Value = "/org.openoffice.Office.Common/Save/Document";

    Object ca = UNO.XMultiServiceFactory(cp).createInstanceWithArguments(
        "com.sun.star.configuration.ConfigurationUpdateAccess", new Object[] { aPathArgument });
    XPropertySet props = UNO.XPropertySet(ca);
    boolean lastValue = AnyConverter.toBoolean(props.getPropertyValue("LoadPrinter"));
    props.setPropertyValue("LoadPrinter", load);
    UnoRuntime.queryInterface(XChangesBatch.class, ca).commitChanges();
    return lastValue;
  }

  /**
   * Erzeugt das aus origDoc abgeleitete, für den OOo-Seriendruck heranzuziehende
   * Input-Dokument im Verzeichnis tmpDir und nimmt alle notwendigen Anpassungen vor,
   * damit der Seriendruck über die temporäre Datenbank dbName korrekt und möglichst
   * performant funktioniert, und liefert dieses zurück.
   * 
   * @author Christoph Lutz (D-III-ITD-D101) TESTED
   */
  private static File createAndAdjustInputFile(File tmpDir, XTextDocument origDoc,
      String dbName)
  {
    // Aktuelles Dokument speichern als neues input-Dokument
    if (origDoc == null) return null;
    File inputFile = new File(tmpDir, "input.odt");
    String url = UNO.getParsedUNOUrl(inputFile.toURI().toString()).Complete;
    XStorable xStorable = UNO.XStorable(origDoc);
    if (xStorable != null)
    {
      try
      {
        xStorable.storeToURL(url, new PropertyValue[] {});
      }
      catch (IOException e)
      {
        LOGGER.error(
          L.m("Kann temporäres Eingabedokument für den OOo-Seriendruck nicht erzeugen"),
          e);
        return null;
      }
    }
    else
    {
      return null;
    }

    // Workaround für #16487
    try
    {
      Thread.sleep(1000);
    }
    catch (InterruptedException e2)
    {
      LOGGER.error("", e2);
    }

    // Neues input-Dokument öffnen. Achtung: Normalerweise würde der
    // loadComponentFromURL den WollMux veranlassen, das Dokument zu interpretieren
    // (und damit zu verarbeiten). Da das bei diesem temporären Dokument nicht
    // erwünscht ist, erkennt der WollMux in
    // d.m.a.i.wollmux.event.GlobalEventListener.isTempMailMergeDocument(XModel
    // compo) über den Pfad der Datei dass es sich um ein temporäres Dokument handelt
    // und dieses nicht bearbeitet werden soll.
    XComponent tmpDoc = null;
    try
    {
      tmpDoc = UNO.loadComponentFromURL(url, false, false, true);
    }
    catch (Exception e)
    {
      return null;
    }

    // neues input-Dokument bearbeiten/anpassen
    addDatabaseFieldsForInsertFormValueBookmarks(UNO.XTextDocument(tmpDoc), dbName);
    updateTextSections(UNO.XTextDocument(tmpDoc));
    adjustDatabaseAndInputUserFields(tmpDoc, dbName);
    removeAllBookmarks(tmpDoc);
    // removeHiddenSections(tmpDoc);
    SachleitendeVerfuegung.deMuxSLVStyles(UNO.XTextDocument(tmpDoc));
    removeWollMuxMetadata(UNO.XTextDocument(tmpDoc));

    // neues input-Dokument speichern und schließen
    if (UNO.XStorable(tmpDoc) != null)
    {
      try
      {
        UNO.XStorable(tmpDoc).store();
      }
      catch (IOException e)
      {
        inputFile = null;
      }
    }
    else
    {
      inputFile = null;
    }

    boolean closed = false;
    if (UNO.XCloseable(tmpDoc) != null) do
    {
      try
      {
        UNO.XCloseable(tmpDoc).close(true);
        closed = true;
      }
      catch (CloseVetoException e)
      {
        try
        {
          Thread.sleep(2000);
        }
        catch (InterruptedException e1)
        {}
      }
    } while (closed == false);

    return inputFile;
  }

  private static void updateTextSections(XTextDocument doc)
  {
    XTextSectionsSupplier tssupp = UNO.XTextSectionsSupplier(doc);
    XNameAccess textSections = tssupp.getTextSections();
    String[] sectionNames = textSections.getElementNames();

    Pattern groupPattern = Pattern.compile(".* GROUPS(?:\\s\"(.*)\"|\\((.*)\\)\n?)");

    for (String sectionName : sectionNames)
    {
      Matcher matcher = groupPattern.matcher(sectionName);
      if (matcher.matches())
      {
        String res = (matcher.group(1) != null) ? matcher.group(1) : matcher.group(2);
        String groups = res.replaceAll("\"", "");
        String[] groupNames = groups.split("\\s*,\\s*");

        try
        {
          XTextSection section =
            UnoRuntime.queryInterface(XTextSection.class,
              textSections.getByName(sectionName));
          XPropertySet ps = UNO.XPropertySet(section);

          XTextRange range = section.getAnchor();
          UNO.setPropertyToDefault(range, "CharHidden");

          List<String> conditions = new ArrayList<>();
          for (String groupName : groupNames)
          {
            conditions.add(String.format("([%s] != \"true\")", COLUMN_PREFIX_TEXTSECTION + groupName));
          }

          String condition = StringUtils.join(conditions, " or ");
          ps.setPropertyValue("IsVisible", false);
          ps.setPropertyValue("Condition", condition);
        }
        catch (Exception e)
        {
          LOGGER.error("", e);
        }
      }
    }
  }

  /**
   * Entfernt alle Metadaten des WollMux aus dem Dokument doc die nicht reine
   * Infodaten des WollMux sind (wie z.B. WollMuxVersion, OOoVersion) um
   * sicherzustellen, dass der WollMux das Gesamtdokument nicht interpretiert.
   * 
   * @author Christoph Lutz (D-III-ITD-D101) TESTED
   */
  private static void removeWollMuxMetadata(XTextDocument doc)
  {
    if (doc == null) return;
    PersistentDataContainer c = DocumentManager.createPersistentDataContainer(doc);
    for (DataID dataId : DataID.values())
      if (!dataId.isInfodata()) c.removeData(dataId);
    c.flush();
  }

  /**
   * Hebt alle unsichtbaren TextSections (Bereiche) in Dokument tmpDoc auf, wobei bei
   * auch der Inhalt entfernt wird. Das Entfernen der unsichtbaren Bereiche dient zur
   * Verbesserung der Performance, das Löschen der Bereichsinhalte ist notwendig,
   * damit das erzeugte Gesamtdokument korrekt dargestellt wird (hier habe ich wilde
   * Textverschiebungen beobachtet, die so vermieden werden sollen).
   *
   * Bereiche sind auch ein möglicher Auslöser von allen möglichen falsch gesetzten
   * Seitenumbrüchen (siehe z.B. Issue:
   * http://openoffice.org/bugzilla/show_bug.cgi?id=73229)
   * 
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  private static void removeHiddenSections(XComponent tmpDoc)
  {
    XTextSectionsSupplier tss = UNO.XTextSectionsSupplier(tmpDoc);
    if (tss == null) return;

    for (String name : tss.getTextSections().getElementNames())
    {
      try
      {
        XTextSection section =
          UNO.XTextSection(tss.getTextSections().getByName(name));
        if (Boolean.FALSE.equals(UNO.getProperty(section, "IsVisible")))
        {
          // Inhalt der Section löschen und Section aufheben:
          section.getAnchor().setString("");
          section.getAnchor().getText().removeTextContent(section);
        }
      }
      catch (Exception e)
      {
        LOGGER.error("", e);
      }
    }
  }

  /**
   * Aufgrund eines Bugs in OOo führen Bookmarks zu einer Verlangsamung des
   * Seriendruck in der Komplexität O(n^2) und werden hier in dieser Methode alle aus
   * dem Dokument tmpDoc gelöscht. Bookmarks sollten im Ergebnisdokument sowieso
   * nicht mehr benötigt werden und sind damit aus meiner Sicht überflüssig.
   *
   * Sollte irgendjemand irgendwann zu der Meinung kommen, dass die Bookmarks im
   * Dokument bleiben müssen, so müssen zumindest die Bookmarks von
   * WollMux-Dokumentkommandos gelöscht werden, damit sie nicht noch einmal durch den
   * WollMux bearbeitet werden.
   * 
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  private static void removeAllBookmarks(XComponent tmpDoc)
  {
    if (UNO.XBookmarksSupplier(tmpDoc) != null)
    {
      XNameAccess xna = UNO.XBookmarksSupplier(tmpDoc).getBookmarks();
      for (String name : xna.getElementNames())
      {
        XTextContent bookmark = null;
        try
        {
          bookmark = UNO.XTextContent(xna.getByName(name));
        }
        catch (NoSuchElementException e)
        {
          continue;
        }
        catch (Exception e)
        {
          LOGGER.error("", e);
        }

        if (bookmark != null)
        {
          try
          {
            bookmark.getAnchor().getText().removeTextContent(bookmark);
          } catch (NoSuchElementException e1)
          {
            LOGGER.error("", e1);
          }
        }
      }
    }
  }

  /**
   * Fügt dem Dokument doc für alle enthaltenen insertFormValue-Bookmarks zugehörige
   * OOo-Seriendruckfelder mit Verweis auf die Datenbank dbName hinzu.
   * 
   * @author Christoph Lutz (D-III-ITD-D101) TESTED
   */
  private static void addDatabaseFieldsForInsertFormValueBookmarks(
      XTextDocument doc, String dbName)
  {
    DocumentCommands cmds = new DocumentCommands(UNO.XBookmarksSupplier(doc));
    cmds.update();
    HashMap<String, FormField> bookmarkNameToFormField =
      new HashMap<>();
    for (DocumentCommand cmd : cmds)
    {
      if (cmd instanceof InsertFormValue)
      {
        InsertFormValue ifvCmd = (InsertFormValue) cmd;
        FormField field =
          FormFieldFactory.createFormField(doc, ifvCmd, bookmarkNameToFormField);
        if (field == null) continue;
        field.setCommand(ifvCmd);

        String columnName = getSpecialColumnNameForFormField(field);
        if (columnName == null) columnName = ifvCmd.getID();
        try
        {
          XDependentTextField dbField =
            createDatabaseField(UNO.XMultiServiceFactory(doc), dbName, TABLE_NAME,
              columnName);
          if (dbField == null) continue;

          ifvCmd.insertTextContentIntoBookmark(dbField, true);

          // Checkboxen müssen über bestimmte Zeichen der Schriftart OpenSymbol
          // angenähert werden.
          if (field.getType() == FormFieldType.CHECKBOX_FORM_FIELD)
            UNO.setProperty(ifvCmd.getTextCursor(), "CharFontName", "OpenSymbol");
        }
        catch (Exception e)
        {
          LOGGER.error("", e);
        }
      }
    }
  }

  /**
   * Liefert zum Formularfeld field unter Berücksichtigung des Feld-Typs und evtl.
   * gesetzter Trafos eine eindeutige Bezeichnung für die Datenbankspalte in die der
   * Wert des Formularfeldes geschrieben ist bzw. aus der der Wert des Formularfeldes
   * wieder ausgelesen werden kann oder null, wenn das Formularfeld über einen
   * primitiven Spaltennamen (der nur aus einer in den setValues gesetzten IDs
   * besteht) gefüllt werden kann.
   * 
   * @author Christoph Lutz (D-III-ITD-D101) TESTED
   */
  private static String getSpecialColumnNameForFormField(FormField field)
  {
    String trafo = field.getTrafoName();
    String id = field.getId();

    if (field.getType() == FormFieldType.CHECKBOX_FORM_FIELD && id != null
      && trafo != null)
      return COLUMN_PREFIX_CHECKBOX_FUNCTION + SEP + id + SEP + trafo;

    else if (field.getType() == FormFieldType.CHECKBOX_FORM_FIELD && id != null
      && trafo == null)
      return COLUMN_PREFIX_CHECKBOX_FUNCTION + SEP + id;

    else if (field.singleParameterTrafo() && id != null && trafo != null)
      return COLUMN_PREFIX_SINGLE_PARAMETER_FUNCTION + SEP + id + SEP + trafo;

    else if (!field.singleParameterTrafo() && trafo != null)
      return COLUMN_PREFIX_MULTI_PARAMETER_FUNCTION + SEP + trafo;

    return null;
  }

  /**
   * Passt bereits enthaltene OOo-Seriendruckfelder und Nächster-Datensatz-Felder in
   * tmpDoc so an, dass sie über die Datenbank dbName befüllt werden und ersetzt
   * InputUser-Felder durch entsprechende OOo-Seriendruckfelder.
   * 
   * @author Christoph Lutz (D-III-ITD-D101) TESTED
   */
  private static void adjustDatabaseAndInputUserFields(XComponent tmpDoc,
      String dbName)
  {
    if (UNO.XTextFieldsSupplier(tmpDoc) != null)
    {
      XEnumeration xenum =
        UNO.XTextFieldsSupplier(tmpDoc).getTextFields().createEnumeration();
      while (xenum.hasMoreElements())
      {
        XDependentTextField tf = null;
        try
        {
          tf = UNO.XDependentTextField(xenum.nextElement());
        }
        catch (Exception e)
        {
          continue;
        }

        // Database-Felder anpassen auf temporäre Datenquelle/Tabelle
        if (UNO.supportsService(tf, "com.sun.star.text.TextField.Database"))
        {
          XPropertySet master = tf.getTextFieldMaster();
          Utils.setProperty(master, "DataBaseName", dbName);
          Utils.setProperty(master, "DataTableName", TABLE_NAME);
        }

        // "Nächster Datensatz"-Felder anpassen auf temporäre Datenquelle/Tabelle
        if (UNO.supportsService(tf, "com.sun.star.text.TextField.DatabaseNextSet"))
        {
          Utils.setProperty(tf, "DataBaseName", dbName);
          Utils.setProperty(tf, "DataTableName", TABLE_NAME);
        }

        // InputUser-Felder ersetzen durch entsprechende Database-Felder
        else if (UNO.supportsService(tf, "com.sun.star.text.TextField.InputUser"))
        {
          String content = "";
          try
          {
            content = AnyConverter.toString(Utils.getProperty(tf, "Content"));
          }
          catch (IllegalArgumentException e)
          {}

          String trafo = TextDocumentModel.getFunctionNameForUserFieldName(content);
          if (trafo != null)
          {
            try
            {
              XDependentTextField dbField =
                createDatabaseField(UNO.XMultiServiceFactory(tmpDoc), dbName,
                  TABLE_NAME, COLUMN_PREFIX_MULTI_PARAMETER_FUNCTION + SEP + trafo);
              tf.getAnchor().getText().insertTextContent(tf.getAnchor(), dbField,
                true);
            }
            catch (Exception e)
            {
              LOGGER.error("", e);
            }
          }
        }
      }
    }
  }

  /**
   * Zählt die Anzahl an "Nächster Datensatz"-Feldern zur Berechnung der Gesamtzahl
   * der zu verarbeitenden Dokumente.
   * 
   * @author Ignaz Forster (ITM-I23)
   */
  private static int countNextSets(XComponent doc)
  {
    int numberOfNextSets = 1;
    if (UNO.XTextFieldsSupplier(doc) != null)
    {
      XEnumeration xenum =
        UNO.XTextFieldsSupplier(doc).getTextFields().createEnumeration();
      while (xenum.hasMoreElements())
      {
        XDependentTextField tf = null;
        try
        {
          tf = UNO.XDependentTextField(xenum.nextElement());
        }
        catch (Exception e)
        {
          continue;
        }

        if (UNO.supportsService(tf, "com.sun.star.text.TextField.DatabaseNextSet"))
        {
          numberOfNextSets++;
        }
      }
    }
    return numberOfNextSets;
  }

  /**
   * Erzeugt über die Factory factory ein neues OOo-Seriendruckfeld, das auf die
   * Datenbank dbName, die Tabelle tableName und die Spalte columnName verweist und
   * liefert dieses zurück.
   *
   * @throws Exception
   *           Wenn die Factory das Feld nicht erzeugen kann.
   * @throws IllegalArgumentException
   *           Wenn irgendetwas mit den Attributen dbName, tableName oder columnName
   *           nicht stimmt.
   * 
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  private static XDependentTextField createDatabaseField(
      XMultiServiceFactory factory, String dbName, String tableName,
      String columnName) throws Exception, IllegalArgumentException
  {
    XDependentTextField dbField =
      UNO.XDependentTextField(factory.createInstance("com.sun.star.text.TextField.Database"));
    XPropertySet m =
      UNO.XPropertySet(factory.createInstance("com.sun.star.text.FieldMaster.Database"));
    UNO.setProperty(m, "DataBaseName", dbName);
    UNO.setProperty(m, "DataTableName", tableName);
    UNO.setProperty(m, "DataColumnName", columnName);
    dbField.attachTextFieldMaster(m);
    return dbField;
  }

  /**
   * Deregistriert die Datenbank dbName aus der Liste der Datenbanken (wie z.B. über
   * Extras->Optionen->Base/Datenbanken einsehbar) und löscht das zugehörige in
   * tmpDir enthaltene .odb-File von der Platte.
   * 
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  private static void removeTempDatasource(String dbName, File tmpDir)
  {
    XSingleServiceFactory dbContext =
      UNO.XSingleServiceFactory(UNO.createUNOService("com.sun.star.sdb.DatabaseContext"));
    XNamingService naming = UNO.XNamingService(dbContext);
    if (naming != null)
    {
      try
      {
        naming.revokeObject(dbName);
      } catch (Exception e)
      {
        LOGGER.error("", e);
      }
    }
    new File(tmpDir, DATASOURCE_ODB_FILENAME).delete();
  }

  /**
   * Registriert die {@link XDocumentDataSource} dataSource mit einem neuen
   * Zufallsnamen in OOo (so, dass sie z.B. in der Liste der Datenbanken unter
   * Tools->Extras->Optionen->Base/Datenbanken auftaucht) und gibt den Zufallsnamen
   * zurück.
   * 
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  private static String registerTempDatasouce(XDocumentDataSource dataSource)
  {
    // neuen Zufallsnamen für Datenquelle bestimmen
    XSingleServiceFactory dbContext =
      UNO.XSingleServiceFactory(UNO.createUNOService("com.sun.star.sdb.DatabaseContext"));
    String name = null;
    XNameAccess nameAccess = UNO.XNameAccess(dbContext);
    if (nameAccess != null) do
    {
      name = TEMP_WOLLMUX_MAILMERGE_PREFIX + new Random().nextInt(100000);
    } while (nameAccess.hasByName(name));

    // Datenquelle registrieren
    if (name != null && UNO.XNamingService(dbContext) != null)
    {
      try
      {
        UNO.XNamingService(dbContext).registerObject(name, dataSource);
      } catch (Exception e)
      {
        LOGGER.error("", e);
      }
    }

    return name;
  }

  /**
   * Steuert den Ausgabetyp beim OOo-Seriendruck.
   *
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  public static enum OutputType {
    toFile,
    toPrinter,
    toShell;

    /**
     * Diese Methode verwendet die Reflection API um den passenden
     * com.sun.star.text.MailMergeType für den OutputType zurück zu liefern oder
     * null, wenn die verwendete unoil.jar den MailMergeType nicht kennt (kann
     * insbes. bei toShell der Fall sein). Hintergrund: MailMergeType.SHELL ist erst
     * ab LibreOffice 4.4 verfügbar. Wir müssen also diesen Weg gehen, damit WollMux
     * kompatibel zu OOo 3.2.1 und AOO bleibt (Build-Zeit und Laufzeit).
     */
    public Short getUNOMailMergeType()
    {
      Field found = null;
      for (Field f : MailMergeType.class.getDeclaredFields())
      {
        switch (this)
        {
          case toFile:
            if (f.getName().equals("FILE")) found = f;
            break;
          case toPrinter:
            if (f.getName().equals("PRINTER")) found = f;
            break;
          case toShell:
            if (f.getName().equals("SHELL")) found = f;
            break;
        }
      }
      if (found != null)
      {
        try
        {
          return found.getShort(null);
        }
        catch (Exception e)
        {
          LOGGER.error("", e);
        }
      }
      return null;
    }
  }

  /**
   * Startet die Ausführung des Seriendrucks in ein Gesamtdokument mit dem
   * c.s.s.text.MailMergeService in einem eigenen Thread und liefert diesen zurück.
   *
   * @param dbName
   *          Name der Datenbank, die für den Seriendruck verwendet werden soll.
   * @param outputDir
   *          Directory in dem das Ergebnisdokument abgelegt werden soll.
   * @param inputFile
   *          Hauptdokument, das für den Seriendruck herangezogen wird.
   * @param progress
   *          Ein ProgressUpdater, der über den Bearbeitungsfortschritt informiert
   *          wird.
   * @param printerName
   *          Drucker fuer den Seriendruck
   * @throws Exception
   *           falls der MailMergeService nicht erzeugt werden kann.
   *
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  private static MailMergeThread runMailMerge(String dbName, final File outputDir,
      File inputFile, final ProgressUpdater progress, final OutputType type) throws Exception
  {
    final XJob mailMerge =
      UnoRuntime.queryInterface(XJob.class, UNO.xMCF.createInstanceWithContext(
        "com.sun.star.text.MailMerge", UNO.defaultContext));

    // Register MailMergeEventListener
    XMailMergeBroadcaster xmmb =
      UnoRuntime.queryInterface(XMailMergeBroadcaster.class, mailMerge);
    xmmb.addMailMergeEventListener(new XMailMergeListener()
    {
      int count = 0;

      final long start = System.currentTimeMillis();

      @Override
      public void notifyMailMergeEvent(MailMergeEvent event)
      {
        if (progress != null) progress.incrementProgress();
        count++;
        LOGGER.trace(L.m("OOo-MailMerger: verarbeite Datensatz %1 (%2 ms)", count,
          (System.currentTimeMillis() - start)));
        if (count >= progress.maxDatasets && type == OutputType.toPrinter)
        {
          progress.setMessage(L.m("Sende Druckauftrag - bitte warten..."));
        }
      }
    });

    final ArrayList<NamedValue> mmProps = new ArrayList<>();
    mmProps.add(new NamedValue("DataSourceName", dbName));
    mmProps.add(new NamedValue("CommandType", CommandType.TABLE));
    mmProps.add(new NamedValue("Command", TABLE_NAME));
    mmProps.add(new NamedValue("DocumentURL",
      UNO.getParsedUNOUrl(inputFile.toURI().toString()).Complete));
    mmProps.add(new NamedValue("OutputURL",
      UNO.getParsedUNOUrl(outputDir.toURI().toString()).Complete));
    mmProps.add(new NamedValue("OutputType", type.getUNOMailMergeType()));
    if (type == OutputType.toFile)
    {
      mmProps.add(new NamedValue("SaveAsSingleFile", Boolean.TRUE));
      mmProps.add(new NamedValue("FileNameFromColumn", Boolean.FALSE));
      mmProps.add(new NamedValue("FileNamePrefix", "output"));
    }
    else if (type == OutputType.toShell)
    {
      mmProps.add(new NamedValue("SaveAsSingleFile", Boolean.TRUE));
      mmProps.add(new NamedValue("FileNameFromColumn", Boolean.FALSE));
    }
    else if (type == OutputType.toPrinter)
    {
      mmProps.add(new NamedValue("SinglePrintJobs", Boolean.FALSE));
    }
    MailMergeThread t = new MailMergeThread(mailMerge, outputDir, mmProps);
    t.start();
    return t;
  }

  /**
   * Erzeugt ein neues temporäres Directory mit dem Aufbau
   * "<TEMP_WOLLMUX_MAILMERGE_PREFIX>xxx" (wobei xxx eine garantiert 3-stellige Zahl
   * ist), in dem sämtliche (temporäre) Dateien für den Seriendruck abgelegt werden
   * und liefert dieses zurück.
   * 
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  public static File createMailMergeTempdir()
  {
    File sysTmp = new File(System.getProperty("java.io.tmpdir"));
    File tmpDir;
    do
    {
      // +100 um eine 3-stellige Zufallszahl zu garantieren
      tmpDir =
        new File(sysTmp, TEMP_WOLLMUX_MAILMERGE_PREFIX
          + (new Random().nextInt(899) + 100));
    } while (!tmpDir.mkdir());
    return tmpDir;
  }
}
