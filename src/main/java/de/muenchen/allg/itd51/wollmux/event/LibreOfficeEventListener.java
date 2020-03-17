package de.muenchen.allg.itd51.wollmux.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.star.beans.PropertyValue;
import com.sun.star.document.XEventListener;
import com.sun.star.frame.XFrame;
import com.sun.star.frame.XModel;
import com.sun.star.lang.EventObject;
import com.sun.star.lang.XComponent;
import com.sun.star.text.XTextDocument;

import de.muenchen.allg.afid.UNO;
import de.muenchen.allg.itd51.wollmux.core.util.L;
import de.muenchen.allg.itd51.wollmux.dispatch.DispatchProviderAndInterceptor;
import de.muenchen.allg.itd51.wollmux.document.DocumentManager;
import de.muenchen.allg.itd51.wollmux.document.DocumentManager.Info;
import de.muenchen.allg.itd51.wollmux.event.handlers.OnCheckInstallation;
import de.muenchen.allg.itd51.wollmux.event.handlers.OnInitialize;
import de.muenchen.allg.itd51.wollmux.event.handlers.OnNotifyDocumentEventListener;
import de.muenchen.allg.itd51.wollmux.event.handlers.OnProcessTextDocument;
import de.muenchen.allg.itd51.wollmux.event.handlers.OnTextDocumentClosed;
import de.muenchen.allg.itd51.wollmux.event.handlers.OnTextDocumentControllerInitialized;

/**
 * A listener for LibreOffice events of documents like OnNew or OnLoad. <a href=
 * "https://wiki.openoffice.org/wiki/Documentation/DevGuide/WritingUNO/Jobs/List_of_Supported_Events">List
 * of supported events</a>
 *
 * The processing state of all documents should be recorded by this listener.
 */
public class LibreOfficeEventListener implements XEventListener
{

  private static final Logger LOGGER = LoggerFactory
      .getLogger(LibreOfficeEventListener.class);

  private static final String ON_SAVE_AS = "OnSaveAs";

  private static final String ON_SAVE = "OnSave";

  private static final String ON_UNLOAD = "OnUnload";

  private static final String ON_CREATE = "OnCreate";

  private static final String ON_VIEW_CREATED = "OnViewCreated";

  private DocumentManager docManager;

  /**
   * Create a new listener for LibreOffice events.
   *
   * @param docManager
   *          The manager of LibreOffice documents.
   */
  public LibreOfficeEventListener(DocumentManager docManager)
  {
    this.docManager = docManager;
  }

  @Override
  @SuppressWarnings("squid:S1181")
  public void notifyEvent(com.sun.star.document.EventObject docEvent)
  {
    // prevent crashes of LibreOffice
    try
    {
      // ignore events without source, as they're irrelevant for Wollmux
      if (docEvent.Source == null)
      {
        return;
      }
      String event = docEvent.EventName;
      LOGGER.trace(event);

      switch (event)
      {
      case ON_CREATE:
        // onCreate(docEvent.Source);
        break;
      case ON_VIEW_CREATED:
        onViewCreated(docEvent.Source);
        break;
      case ON_UNLOAD:
        onUnload(docEvent.Source);
        break;
      case ON_SAVE:
      case ON_SAVE_AS:
        onSaveOrSaveAs(docEvent.Source);
        break;
      default:
        // nothing to do
      }
    }
    catch (Throwable t)
    {
      LOGGER.error("", t);
    }
  }

  /**
   * OnCreate is the first event emitted as soon as a new empty document is created. The following
   * actions cause such an event:
   * <ul>
   * <li>{@code loadComponentFromURL("private:factory/swriter", ...) }</li>
   * <li>file &gt; new, if there's no default template</li>
   * <li>LibreOffice mailmerge</li>
   * <li>Insertion of auto texts (bt&lt;F3&gt;)</li>
   * </ul>
   *
   * Opening a file or a template doesn't create this event.
   */
  private void onCreate(Object source)
  {
    XComponent compo = UNO.XComponent(source);
    if (compo == null)
    {
      return;
    }

    XModel compoModel = UNO.XModel(compo);

    if (compoModel == null || isTempMailMergeDocument(compoModel))
    {
      return;
    }

    // we add the document to the manager, so we know in onViewCreated that it's a new document
    XTextDocument xTextDoc = UNO.XTextDocument(source);
    if (xTextDoc != null)
    {
      docManager.addTextDocument(xTextDoc);
    } else
    {
      docManager.add(compo);
    }
  }

  /**
   * OnViewCreated events are emitted as soon as the document is fully build. It doesn't matter if
   * the file is loaded or created, visible or invisible.
   *
   * It's emitted after the sidebar is initialized. So if we need the document during instantiation
   * we have to use OnLoadFinished as well.
   *
   * Processing of documents by WollMux is started here. Temporary files are ignored
   * ({@link #isTempMailMergeDocument(XModel)}.
   */
  private void onViewCreated(Object source)
  {
    XModel compo = UNO.XModel(source);

    if (compo == null)
      return;

    // no action for temporary files
    if (isTempMailMergeDocument(compo))
    {
      LOGGER.debug(
          "Document is an mail merge document. Further processing is not required. Returning.");
      return;
    }

    // Check installation is only executed on first event because the listener is unregistered
    // afterwards.
    new OnCheckInstallation().emit();
    new OnInitialize().emit();

    XTextDocument xTextDoc = UNO.XTextDocument(compo);
    Info docInfo = docManager.getInfo(compo);

    if (xTextDoc != null)
      registerDispatcher(compo.getCurrentController().getFrame()); // wird für jedes neue
                                                                   // xtextdocument registriert?
    // kann es was damit zu tun haben das bei PersonelicheAbsenderListeVerwalten der keylistener auf
    // ein Control nicht funktioniert.

    // start processing
    if (docInfo == null)
    {
      if (xTextDoc != null)
      {
        docManager.addTextDocument(xTextDoc);
        new OnProcessTextDocument(DocumentManager.getTextDocumentController(xTextDoc)).emit();
      } else
      {
        docManager.add(compo);
        new OnNotifyDocumentEventListener(null, WollMuxEventHandler.ON_WOLLMUX_PROCESSING_FINISHED,
            compo).emit();
      }
    }

    if (xTextDoc != null)
    {
      new OnTextDocumentControllerInitialized(xTextDoc)
          .emit();
    }
  }

  /**
   * OnSave and OnSaveAs events are emitted as soon as a document is saved.
   *
   * WollMux has to save its persistent data.
   */
  private void onSaveOrSaveAs(Object source)
  {
    XTextDocument xTextDoc = UNO.XTextDocument(source);
    if (xTextDoc == null)
    {
      return;
    }
    DocumentManager.Info info = docManager.getInfo(xTextDoc);
    if (info != null && info.hasTextDocumentModel())
    {
      info.getTextDocumentController().flushPersistentData();
    }
  }

  /**
   * OnUnload is the last event emitted as soon as a document is closed.
   *
   * WollMux has to clean up its listeners.
   */
  private void onUnload(Object source)
  {
    DocumentManager.Info info = docManager.remove(source);

    // info is null, if it's a temporary file.
    if (info != null)
    {
      new OnTextDocumentClosed(info).emit();
    }
  }

  /**
   * Check whether we have a temporary file of mailmerge. Temporary files are either
   * <ul>
   * <li>have a name ending with ".tmp"</li>
   * <li>have a name starting with ".tmp/"</li>
   * <li>have a name starting with "SwMM"</li>
   * <li>have a name starting with "WollMuxMailMerge"</li>
   * <li>have the name "private:object" and is invisible</li>
   * <ul>
   *
   * @param compo
   *          A document.
   * @return True if one of the properties above is true, false otherwise.
   */
  private boolean isTempMailMergeDocument(XModel compo)
  {
    String url = compo.getURL();
    int idx = url.lastIndexOf('/');
    PropertyValue[] args = compo.getArgs();
    String fileName = "";
    boolean hidden = false;
    for (PropertyValue p : args)
    {
      if (p.Name.equals("FileName"))
      {
	fileName = (String) p.Value;
      } else if (p.Name.equals("Hidden"))
      {
	hidden = (Boolean) p.Value;
      }
    }
    // file -> print to mail merge
    boolean tmp = url.startsWith(".tmp/", idx - 4) && url.endsWith(".tmp");
    // create by css.text.MailMerge
    boolean mmService = url.startsWith("/SwMM", idx) && url.endsWith(".odt");
    /*
     * files created by css.text.MailMerge have only 3 properties whereas normal
     * files have far more.
     */
    mmService |= args.length == 3;
    // create by WollMux mail merge
    boolean wmMailmerge = url.startsWith("/WollMuxMailMerge", idx - 20);

    return tmp || mmService || wmMailmerge
        || (fileName.equals("private:object") && hidden);
  }

  @Override
  public void disposing(EventObject arg0)
  {
    // nothing to do
  }

  /**
   * Register a dispatcher for the frame.
   *
   * @param frame
   *          The frame of a document.
   */
  private void registerDispatcher(XFrame frame)
  {
    if (frame == null)
    {
      LOGGER.debug("Ignoriere handleRegisterDispatchInterceptor(null)");
      return;
    }
    try
    {
      DispatchProviderAndInterceptor.registerDocumentDispatchInterceptor(frame);
    }
    catch (java.lang.Exception e)
    {
      LOGGER.error(L.m("Kann DispatchInterceptor nicht registrieren:"), e);
    }

    // update toolbar
    try
    {
      frame.contextChanged();
    }
    catch (java.lang.Exception e)
    {
      LOGGER.debug("", e);
    }
  }
}
