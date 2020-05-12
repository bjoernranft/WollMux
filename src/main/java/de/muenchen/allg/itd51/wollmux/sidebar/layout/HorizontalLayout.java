package de.muenchen.allg.itd51.wollmux.sidebar.layout;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import com.sun.star.awt.Rectangle;

/**
 * A horizontal layout. The layouts are shown in one row.
 *
 * The width of each layout is computed based on their weight.
 */
public class HorizontalLayout implements Layout
{
  /**
   * Container for layouts.
   */
  private Map<Layout, Integer> layouts = new LinkedHashMap<>();

  /**
   * Margin above first layout.
   */
  private int marginTop;

  /**
   * margin between layouts.
   */
  private int marginBetween;

  /**
   * Horizontal layout without margin.
   */
  public HorizontalLayout()
  {
    this(0, 0);
  }

  /**
   * Horizontal layout with space between the elements.
   *
   * @param marginTop
   *          Space before the first element.
   * @param marginBetween
   *          Space between the elements
   */
  public HorizontalLayout(int marginTop, int marginBetween)
  {
    this.marginTop = marginTop;
    this.marginBetween = marginBetween;
  }

  @Override
  public Pair<Integer, Integer> layout(Rectangle rect)
  {
    int xOffset = 0;
    int marginTotal = (layouts.size() - 1) * marginBetween;
    int width = (rect.Width - marginTotal) / layouts.values().stream().reduce(0, Integer::sum);
    int height = 0;

    for (Map.Entry<Layout, Integer> entry : layouts.entrySet())
    {
      Pair<Integer, Integer> size = entry.getKey()
          .layout(new Rectangle(rect.X + xOffset, rect.Y + marginTop, width * entry.getValue(), rect.Height));
      height = Integer.max(height, size.getLeft());
      xOffset += size.getRight() + marginBetween;
    }

    height += marginTop;
    return Pair.of(height, rect.Width);
  }

  @Override
  public void addLayout(Layout layout, int weight)
  {
    layouts.put(layout, weight);
  }

  @Override
  public int getHeightForWidth(int width)
  {
    int h = layouts.keySet().stream().mapToInt(l -> l.getHeightForWidth(width)).max().orElse(0);
    if (h > 0)
    {
      h += marginTop;
    }
    return h;
  }

  @Override
  public int getMinimalWidth(int maxWidth)
  {
    int margin = (layouts.size() - 1) * marginBetween;
    int width = (maxWidth - margin) / layouts.values().stream().reduce(0, Integer::sum);
    int minWidth = 0;
    for (Map.Entry<Layout, Integer> l : layouts.entrySet())
    {
      minWidth += l.getKey().getMinimalWidth(l.getValue() * width);
    }
    if (minWidth > 0)
    {
      minWidth += margin;
    }
    return minWidth;
  }
}
