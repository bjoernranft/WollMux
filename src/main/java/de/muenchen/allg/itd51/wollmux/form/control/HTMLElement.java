package de.muenchen.allg.itd51.wollmux.form.control;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.star.awt.FontDescriptor;

/**
 * Represents the model of an HTML Element.
 */
public class HTMLElement
{

  private static final Logger LOGGER = LoggerFactory.getLogger(HTMLElement.class);

  private String tagName = "";
  private String text = "";
  private String href = "";
  private int rgbColor;
  private FontDescriptor fontDescriptor;

  public FontDescriptor getFontDescriptor()
  {
    return fontDescriptor;
  }

  public void setFontDescriptor(FontDescriptor fontDescriptor)
  {
    this.fontDescriptor = fontDescriptor;
  }

  public int getRGBColor()
  {
    return this.rgbColor;
  }

  public void setRGBColor(int color)
  {
    this.rgbColor = color;
  }

  public void setHref(String href)
  {
    this.href = href;
  }

  public void setTagName(String tagName)
  {
    this.tagName = tagName;
  }

  public void setText(String text)
  {
    this.text = text;
  }

  public String getHref()
  {
    return this.href;
  }

  public String getTagName()
  {
    return this.tagName;
  }

  public String getText()
  {
    return this.text;
  }

  /**
   * Parses an html string with java swing's {@link HTMLEditorKit} to {@link HTMLElement}-Model.
   *
   * @param html
   *          HTML string.
   * @return List of HTML-Elements.
   */
  public static List<HTMLElement> parseHtml(String html)
  {
    Reader stringReader = new StringReader(html);
    HTMLEditorKit.Parser parser = new ParserDelegator();
    HTMLParserCallback callback = new HTMLParserCallback();

    try
    {
      parser.parse(stringReader, callback, true);
    } catch (IOException e)
    {
      LOGGER.trace("", e);
    } finally
    {
      try
      {
        stringReader.close();
      } catch (IOException e)
      {
        LOGGER.trace("", e);
      }
    }

    return callback.getHtmlElement();
  }

  /**
   * Converts <br>
   * to \r\n for LO's text field
   *
   * @param html
   *          HTML string.
   * @return cleaned html.
   */
  public static String convertLineBreaks(String html)
  {
    return html.replace("<br>", "\r\n");
  }

}
