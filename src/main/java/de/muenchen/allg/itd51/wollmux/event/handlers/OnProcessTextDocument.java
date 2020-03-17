package de.muenchen.allg.itd51.wollmux.event.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.muenchen.allg.itd51.wollmux.GlobalFunctions;
import de.muenchen.allg.itd51.wollmux.WollMuxFehlerException;
import de.muenchen.allg.itd51.wollmux.WollMuxFiles;
import de.muenchen.allg.itd51.wollmux.core.document.WMCommandsFailedException;
import de.muenchen.allg.itd51.wollmux.core.form.model.FormModelException;
import de.muenchen.allg.itd51.wollmux.core.parser.ConfigThingy;
import de.muenchen.allg.itd51.wollmux.core.parser.ConfigurationErrorException;
import de.muenchen.allg.itd51.wollmux.core.parser.NodeNotFoundException;
import de.muenchen.allg.itd51.wollmux.document.TextDocumentController;
import de.muenchen.allg.itd51.wollmux.document.commands.DocumentCommandInterpreter;
import de.muenchen.allg.itd51.wollmux.event.WollMuxEventHandler;
import de.muenchen.allg.itd51.wollmux.form.control.FormController;

/**
 * Event for processing a document.
 */
public class OnProcessTextDocument extends WollMuxEvent
{
  private static final Logger LOGGER = LoggerFactory.getLogger(OnProcessTextDocument.class);

  private TextDocumentController documentController;

  /**
   * Create this event.
   *
   * @param documentController
   *          The document to process.
   * @param visible
   *          If false, the window of the document is invisible, otherwise it's visible.
   */
  public OnProcessTextDocument(TextDocumentController documentController)
  {
    this.documentController = documentController;
  }

  @Override
  protected void doit() throws WollMuxFehlerException
  {
    if (documentController == null)
    {
      return;
    }

    try
    {
      ConfigThingy tds = WollMuxFiles.getWollmuxConf().query("Fenster").query("Textdokument")
          .getLastChild();
      documentController.getFrameController().setWindowViewSettings(tds);
    } catch (NodeNotFoundException e)
    {
      // configuration for Fenster isn't mandatory
    }

    DocumentCommandInterpreter dci = new DocumentCommandInterpreter(documentController,
        WollMuxFiles.isDebugMode());

    // scan global document commands
    dci.scanGlobalDocumentCommands();

    int actions = documentController.evaluateDocumentActions(
        GlobalFunctions.getInstance().getDocumentActionFunctions().iterator());

    // if it is a template execute the commands
    if ((actions < 0 && documentController.getModel().isTemplate())
        || (actions == Integer.MAX_VALUE))
    {
      try
      {
        dci.executeTemplateCommands();
      } catch (WMCommandsFailedException e)
      {
        LOGGER.error("", e);
      }

      // there can be new commands now
      dci.scanGlobalDocumentCommands();
    }

    dci.scanInsertFormValueCommands();

    // if it is a form execute form commands
    if (actions != 0 && documentController.getModel().isFormDocument())
    {
      try
      {
        documentController.getFrameController().setDocumentZoom(WollMuxFiles.getWollmuxConf()
            .query("Fenster").query("Formular").getLastChild().query("ZOOM"));
      } catch (ConfigurationErrorException | NodeNotFoundException e)
      {
        // configuration for Fenster isn't mandatory
        LOGGER.trace("", e);
      }

      try
      {
        FormController formController = documentController.getFormController();
        formController.createFormGUI();
        formController.formControllerInitCompleted();
      } catch (FormModelException e)
      {
        LOGGER.error("No valid formular UI configuration found.", e);
      }
    }

    // notify listeners about processing finished
    new OnNotifyDocumentEventListener(null, WollMuxEventHandler.ON_WOLLMUX_PROCESSING_FINISHED,
        documentController.getModel().doc).emit();

    // ContextChanged to update dispatches
    try
    {
      documentController.getFrameController().getFrame().contextChanged();
    } catch (java.lang.Exception e)
    {
      LOGGER.debug("", e);
    }
  }

  @Override
  public String toString()
  {
    return this.getClass().getSimpleName() + "(#"
        + documentController.hashCode() + ")";
  }
}