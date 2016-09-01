/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.application.options.colors;

import com.intellij.application.options.colors.highlighting.HighlightData;
import com.intellij.application.options.colors.highlighting.HighlightsExtractor;
import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.codeInsight.daemon.UsedColors;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorSchemeAttributeDescriptor;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.CaretAdapter;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.options.colors.EditorHighlightingProvidingColorSettingsPage;
import com.intellij.openapi.options.colors.RainbowColorSettingsPage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Alarm;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SimpleEditorPreview implements PreviewPanel {
  private final ColorSettingsPage myPage;

  private final EditorEx myEditor;
  private final Alarm myBlinkingAlarm;
  private final List<HighlightData> myHighlightData = new ArrayList<>();

  private final ColorAndFontOptions myOptions;

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);
  private final HighlightsExtractor myHighlightsExtractor;

  public SimpleEditorPreview(final ColorAndFontOptions options, final ColorSettingsPage page) {
    this(options, page, true);
  }

  public SimpleEditorPreview(final ColorAndFontOptions options, final ColorSettingsPage page, final boolean navigatable) {
    myOptions = options;
    myPage = page;

    myHighlightsExtractor = new HighlightsExtractor(page.getAdditionalHighlightingTagToDescriptorMap());
    myEditor = (EditorEx)FontEditorPreview.createPreviewEditor(
      myHighlightsExtractor.extractHighlights(page.getDemoText(), myHighlightData), // text without tags
      10, 3, -1, myOptions, false);

    FontEditorPreview.installTrafficLights(myEditor);
    myBlinkingAlarm = new Alarm().setActivationComponent(myEditor.getComponent());
    if (navigatable) {
      myEditor.getContentComponent().addMouseMotionListener(new MouseMotionAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          navigate(false, myEditor.xyToLogicalPosition(new Point(e.getX(), e.getY())));
        }
      });

      myEditor.getCaretModel().addCaretListener(new CaretAdapter() {
        @Override
        public void caretPositionChanged(CaretEvent e) {
          navigate(true, e.getNewPosition());
        }
      });
    }
  }

  public EditorEx getEditor() {
    return myEditor;
  }

  public void setDemoText(final String text) {
    myEditor.getSelectionModel().removeSelection();
    myHighlightData.clear();
    myEditor.getDocument().setText(myHighlightsExtractor.extractHighlights(text, myHighlightData));
  }

  private void navigate(boolean select, @NotNull final LogicalPosition pos) {
    int offset = myEditor.logicalPositionToOffset(pos);
    final SyntaxHighlighter highlighter = myPage.getHighlighter();

    String type = null;
    HighlightData highlightData = getDataFromOffset(offset);
    if (highlightData != null) {
      // tag-based navigation first
      type = RainbowHighlighter.isRainbowTempKey(highlightData.getHighlightKey())
             ? RainbowHighlighter.RAINBOW_TYPE
             : highlightData.getHighlightType();
    }
    else {
      // if failed, try the highlighter-based navigation
      type = selectItem(((EditorEx)myEditor).getHighlighter().createIterator(offset), highlighter);
    }

    setCursor(type == null ? Cursor.TEXT_CURSOR : Cursor.HAND_CURSOR);

    if (select && type != null) {
      myDispatcher.getMulticaster().selectionInPreviewChanged(type);
    }
  }

  @Nullable
  private  HighlightData getDataFromOffset(int offset) {
    for (HighlightData highlightData : myHighlightData) {
      if (offset >= highlightData.getStartOffset() && offset <= highlightData.getEndOffset()) {
        return highlightData;
      }
    }
    return null;
  }

  @Nullable
  private static String selectItem(HighlighterIterator itr, SyntaxHighlighter highlighter) {
    IElementType tokenType = itr.getTokenType();
    if (tokenType == null) return null;

    TextAttributesKey[] highlights = highlighter.getTokenHighlights(tokenType);
    String s = null;
    for (int i = highlights.length - 1; i >= 0; i--) {
      if (highlights[i] != HighlighterColors.TEXT) {
        s = highlights[i].getExternalName();
        break;
      }
    }
    return s == null ? HighlighterColors.TEXT.getExternalName() : s;
  }

  @Override
  public JComponent getPanel() {
    return myEditor.getComponent();
  }

  @Override
  public void updateView() {
    EditorColorsScheme scheme = myOptions.getSelectedScheme();

    myEditor.setColorsScheme(scheme);

    EditorHighlighter highlighter = null;
    if (myPage instanceof EditorHighlightingProvidingColorSettingsPage) {

      highlighter = ((EditorHighlightingProvidingColorSettingsPage)myPage).createEditorHighlighter(scheme);
    }
    if (highlighter == null) {
      final SyntaxHighlighter pageHighlighter = myPage.getHighlighter();
      highlighter = HighlighterFactory.createHighlighter(pageHighlighter, scheme);
    }
    myEditor.setHighlighter(highlighter);
    updateHighlighters();

    myEditor.reinitSettings();
  }

  private void updateHighlighters() {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (myEditor.isDisposed()) return;
      myEditor.getMarkupModel().removeAllHighlighters();
      final Map<TextAttributesKey, String> displayText = ColorSettingsUtil.keyToDisplayTextMap(myPage);
      for (final HighlightData data : myHighlightData) {
        data.addHighlToView(myEditor, myOptions.getSelectedScheme(), displayText);
      }
    });
  }

  private static final int BLINK_COUNT = 3 * 2;

  @Override
  public void blinkSelectedHighlightType(Object description) {
    if (description instanceof EditorSchemeAttributeDescriptor) {
      String type = ((EditorSchemeAttributeDescriptor)description).getType();

      List<HighlightData> highlights = startBlinkingHighlights(myEditor,
                                                               type,
                                                               myPage.getHighlighter(), true,
                                                               myBlinkingAlarm, BLINK_COUNT, myPage);

      scrollHighlightInView(highlights);
    }
  }

  void scrollHighlightInView(@Nullable final List<HighlightData> highlightDatas) {
    if (highlightDatas == null) return;

    boolean needScroll = true;
    int minOffset = Integer.MAX_VALUE;
    for (HighlightData data : highlightDatas) {
      if (isOffsetVisible(data.getStartOffset())) {
        needScroll = false;
        break;
      }
      minOffset = Math.min(minOffset, data.getStartOffset());
    }
    if (needScroll && minOffset != Integer.MAX_VALUE) {
      LogicalPosition pos = myEditor.offsetToLogicalPosition(minOffset);
      myEditor.getScrollingModel().scrollTo(pos, ScrollType.MAKE_VISIBLE);
    }
  }

  private boolean isOffsetVisible(final int startOffset) {
    return myEditor
      .getScrollingModel()
      .getVisibleArea()
      .contains(myEditor.logicalPositionToXY(myEditor.offsetToLogicalPosition(startOffset)));
  }

  public void stopBlinking() {
    myBlinkingAlarm.cancelAllRequests();
  }

  private List<HighlightData> startBlinkingHighlights(final EditorEx editor,
                                                      final String attrKey,
                                                      final SyntaxHighlighter highlighter,
                                                      final boolean show,
                                                      final Alarm alarm,
                                                      final int count,
                                                      final ColorSettingsPage page) {
    if (show && count <= 0) return Collections.emptyList();
    editor.getMarkupModel().removeAllHighlighters();
    boolean found = false;
    List<HighlightData> highlights = new ArrayList<>();
    List<HighlightData> matchingHighlights = new ArrayList<>();
    for (HighlightData highlightData : myHighlightData) {
      String type = highlightData.getHighlightType();
      highlights.add(highlightData);
      if (show && type.equals(attrKey)) {
        highlightData =
          new HighlightData(highlightData.getStartOffset(), highlightData.getEndOffset(),
                            CodeInsightColors.BLINKING_HIGHLIGHTS_ATTRIBUTES);
        highlights.add(highlightData);
        matchingHighlights.add(highlightData);
        found = true;
      }
    }
    if (!found && highlighter != null) {
      HighlighterIterator iterator = editor.getHighlighter().createIterator(0);
      do {
        IElementType tokenType = iterator.getTokenType();
        TextAttributesKey[] tokenHighlights = highlighter.getTokenHighlights(tokenType);
        for (final TextAttributesKey tokenHighlight : tokenHighlights) {
          String type = tokenHighlight.getExternalName();
          if (show && type != null && type.equals(attrKey)) {
            HighlightData highlightData = new HighlightData(iterator.getStart(), iterator.getEnd(),
                                                            CodeInsightColors.BLINKING_HIGHLIGHTS_ATTRIBUTES);
            highlights.add(highlightData);
            matchingHighlights.add(highlightData);
          }
        }
        iterator.advance();
      }
      while (!iterator.atEnd());
    }

    final Map<TextAttributesKey, String> displayText = ColorSettingsUtil.keyToDisplayTextMap(page);

    // sort highlights to avoid overlappings
    Collections.sort(highlights, (highlightData1, highlightData2) -> highlightData1.getStartOffset() - highlightData2.getStartOffset());
    for (int i = highlights.size() - 1; i >= 0; i--) {
      HighlightData highlightData = highlights.get(i);
      int startOffset = highlightData.getStartOffset();
      HighlightData prevHighlightData = i == 0 ? null : highlights.get(i - 1);
      if (prevHighlightData != null
          && startOffset <= prevHighlightData.getEndOffset()
          && highlightData.getHighlightType().equals(prevHighlightData.getHighlightType())) {
        prevHighlightData.setEndOffset(highlightData.getEndOffset());
      }
      else {
        highlightData.addHighlToView(editor, myOptions.getSelectedScheme(), displayText);
      }
    }
    alarm.cancelAllRequests();
    alarm.addComponentRequest(() -> startBlinkingHighlights(editor, attrKey, highlighter, !show, alarm, count - 1, page), 400);
    return matchingHighlights;
  }


  @Override
  public void addListener(@NotNull final ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void disposeUIResources() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    editorFactory.releaseEditor(myEditor);
    stopBlinking();
  }

  private void setCursor(@JdkConstants.CursorType int type) {
    final Cursor cursor = type == Cursor.TEXT_CURSOR ? UIUtil.getTextCursor(myEditor.getBackgroundColor())
                                                     : Cursor.getPredefinedCursor(type);
    myEditor.getContentComponent().setCursor(cursor);
  }

  public void setupRainbow(@NotNull EditorColorsScheme colorsScheme, @NotNull RainbowColorSettingsPage page) {
    final List<HighlightData> initialMarkup = new ArrayList<>();
    myHighlightsExtractor.extractHighlights(page.getDemoText(), initialMarkup);

    final List<HighlightData> rainbowMarkup = setupRainbowHighlighting(
      page,
      initialMarkup,
      new RainbowHighlighter(colorsScheme).getRainbowTempKeys(),
      RainbowHighlighter.isRainbowEnabledWithInheritance(colorsScheme, page.getLanguage()));

    myHighlightData.clear();
    myHighlightData.addAll(rainbowMarkup);
  }

  @NotNull
  private List<HighlightData> setupRainbowHighlighting(@NotNull final RainbowColorSettingsPage page,
                                                       @NotNull final List<HighlightData> initialMarkup,
                                                       @NotNull final TextAttributesKey[] rainbowTempKeys,
                                                       boolean isRainbowOn) {
    int colorCount = rainbowTempKeys.length;
    if (colorCount == 0) {
      return initialMarkup;
    }
    List<HighlightData> rainbowMarkup = new ArrayList<>();

    int tempKeyIndex = 0;
    for (HighlightData d : initialMarkup) {
      final TextAttributesKey highlightKey = d.getHighlightKey();
      final boolean rainbowType = page.isRainbowType(highlightKey);
      final boolean rainbowDemoType = highlightKey == RainbowHighlighter.RAINBOW_GRADIENT_DEMO;
      if (rainbowType || rainbowDemoType) {
        final HighlightData rainbowAnchor = new HighlightData(d.getStartOffset(), d.getEndOffset(), RainbowHighlighter.RAINBOW_ANCHOR);
        if (isRainbowOn) {
          // rainbow on
          HighlightData rainbowTemp;
          if (rainbowType) {
            rainbowTemp = getRainbowTemp(rainbowTempKeys, d.getStartOffset(), d.getEndOffset());
          }
          else {
            rainbowTemp = new HighlightData(d.getStartOffset(), d.getEndOffset(), rainbowTempKeys[tempKeyIndex++ % colorCount]);
          }
          rainbowMarkup.add(rainbowTemp);
          rainbowMarkup.add(rainbowAnchor);
          rainbowMarkup.add(rainbowTemp);
        }
        else {
          // rainbow off
          if (rainbowType) {
            rainbowMarkup.add(d);
            rainbowMarkup.add(rainbowAnchor);
            rainbowMarkup.add(d);
          }
          else {
            rainbowMarkup.add(rainbowAnchor);
          }
        }
      }
      else if (!(RainbowHighlighter.isRainbowTempKey(highlightKey) || highlightKey == RainbowHighlighter.RAINBOW_ANCHOR)) {
        // filter rainbow RAINBOW_TEMP and RAINBOW_ANCHOR
        rainbowMarkup.add(d);
      }
    }
    return rainbowMarkup;
  }

  @NotNull
  private HighlightData getRainbowTemp(@NotNull TextAttributesKey[] rainbowTempKeys,
                                       int startOffset, int endOffset) {
    String id = myEditor.getDocument().getText(TextRange.create(startOffset, endOffset));
    int index = UsedColors.getOrAddColorIndex((EditorImpl)myEditor, id, rainbowTempKeys.length);
    return new HighlightData(startOffset, endOffset, rainbowTempKeys[index]);
  }
}
