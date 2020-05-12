package de.muenchen.allg.itd51.wollmux.document.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;
import com.sun.star.frame.XController2;
import com.sun.star.ui.XDeck;

import de.muenchen.allg.afid.UNO;
import de.muenchen.allg.afid.UnoHelperException;
import de.muenchen.allg.itd51.wollmux.GlobalFunctions;
import de.muenchen.allg.itd51.wollmux.WollMuxFiles;
import de.muenchen.allg.itd51.wollmux.core.parser.ConfigThingy;
import de.muenchen.allg.itd51.wollmux.core.parser.NodeNotFoundException;
import de.muenchen.allg.itd51.wollmux.document.TextDocumentController;
import de.muenchen.allg.itd51.wollmux.event.WollMuxEventHandler;
import de.muenchen.allg.itd51.wollmux.event.WollMuxEventListener;
import de.muenchen.allg.itd51.wollmux.event.handlers.OnNotifyDocumentEventListener;
import de.muenchen.allg.itd51.wollmux.event.handlers.OnTextDocumentControllerInitialized;
import de.muenchen.allg.itd51.wollmux.form.sidebar.FormSidebarController;
import de.muenchen.allg.itd51.wollmux.sidebar.WollMuxSidebarPanel;
import de.muenchen.allg.util.UnoSidebar;

/**
 * Processes text documents.
 */
public class OnProcessTextDocument implements WollMuxEventListener
{
  private static final Logger LOGGER = LoggerFactory.getLogger(OnProcessTextDocument.class);

  /**
   * Processes the document given by the {@link TextDocumentController} of the
   * event.
   *
   * @param event
   *          An {@link OnTextDocumentControllerInitialized} event.
   */
  @Subscribe
  public void onTextDocumentControllerInitialized(OnTextDocumentControllerInitialized event)
  {
    TextDocumentController documentController = event.getTextDocumentController();

    if (documentController == null)
    {
      LOGGER.trace("{} : DocumentController is NULL.", this.getClass().getSimpleName());
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

    DocumentCommandInterpreter dci = new DocumentCommandInterpreter(
        documentController, WollMuxFiles.isDebugMode());

    try
    {
      // scan global document commands
      dci.scanGlobalDocumentCommands();

      int actions = documentController.evaluateDocumentActions(GlobalFunctions
          .getInstance().getDocumentActionFunctions().iterator());

      // if it is a template execute the commands
      if ((actions < 0 && documentController.getModel().isTemplate())
          || (actions == Integer.MAX_VALUE))
      {
        dci.executeTemplateCommands();

        // there can be new commands now
        dci.scanGlobalDocumentCommands();
      }
      dci.scanInsertFormValueCommands();

    } catch (java.lang.Exception e)
    {
      LOGGER.error("", e);
    }

    // notify listeners about processing finished
    new OnNotifyDocumentEventListener(null, WollMuxEventHandler.ON_WOLLMUX_PROCESSING_FINISHED,
        documentController.getModel().doc).emit();

    XController2 controller = UNO.XController2(documentController.getModel().doc.getCurrentController());
    if (documentController.getModel().isFormDocument())
    {
      this.activateSidebarPanel(controller, FormSidebarController.WM_FORM_GUI);
    } else {
      this.activateSidebarPanel(controller, WollMuxSidebarPanel.WM_BAR);
    }

    // ContextChanged to update dispatches
    try
    {
      documentController.getFrameController().getFrame().contextChanged();
    } catch (java.lang.Exception e)
    {
      LOGGER.debug("", e);
    }
  }

  private void activateSidebarPanel(XController2 controller, String panel)
  {
    try
    {
      XDeck formGuiDeck = UnoSidebar.getDeckByName(panel, controller);
      if (formGuiDeck != null)
      {
        formGuiDeck.activate(true);
      }
    } catch (UnoHelperException e)
    {
      LOGGER.trace("", e);
    }
  }

}