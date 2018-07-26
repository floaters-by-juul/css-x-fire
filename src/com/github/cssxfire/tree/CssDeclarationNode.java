/*
 * Copyright 2010 Ronnie Kolehmainen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.cssxfire.tree;

import com.github.cssxfire.CssUtils;
import com.github.cssxfire.CssXFireSettings;
import com.intellij.ide.SelectInEditorManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.css.CssDeclaration;
import com.intellij.psi.css.CssElement;
import com.intellij.psi.css.CssTerm;
import com.intellij.psi.css.CssTermList;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class CssDeclarationNode extends CssTreeNode implements Navigatable {
    private static final Logger LOG = Logger.getInstance(CssDeclarationNode.class);
    protected final CssDeclaration cssDeclaration;
    protected final String value;
    protected boolean deleted;
    protected boolean important;

    public CssDeclarationNode(CssDeclaration cssDeclaration, String value, boolean deleted, boolean important) {
        this.cssDeclaration = cssDeclaration;
        this.value = value;
        this.deleted = deleted;
        this.important = important;
    }

    @Override
    public boolean getAllowsChildren() {
        return false;
    }

    @Override
    public Icon getIcon() {
        return isValid() ? cssDeclaration.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS) : EmptyIcon.ICON_16;
    }

    @Override
    public String getName() {
        return cssDeclaration.getPropertyName();
    }

    @Override
    public String getText() {
        return getName() + ": " + value + (important ? " !important" : "");
    }

    @NotNull
    @Override
    public SimpleTextAttributes getTextAttributes() {
        Color color = isValid() ? FileStatus.MODIFIED.getColor() : FileStatus.DELETED.getColor();
        return super.getTextAttributes().derive(deleted ? SimpleTextAttributes.STYLE_STRIKEOUT : -1, color, null, null);
    }

    public boolean isValid() {
        return cssDeclaration.isValid();
    }

    public String getPropertyName() {
        return cssDeclaration.getPropertyName();
    }

    /**
     * Applies this change to the corresponding source code.<br><br>
     * <b>Note:</b> Must be invoked in a {@link com.intellij.openapi.application.Application#runWriteAction write-action}
     */
    public void applyToCode() {
        try {
            if (isValid()) {
                if (deleted) {
                    PsiElement nextSibling = cssDeclaration.getNextSibling();
                    if (nextSibling != null && ";".equals(nextSibling.getText())) {
                        nextSibling.delete(); // delete trailing semi-colon
                    }
                    cssDeclaration.delete();
                } else {
                    if (cssDeclaration.isImportant() == important) {
                        // Priority not changed - only need to alter the value text.
                        CssElement navigationElement = getNavigationElement();
                        if (navigationElement instanceof CssTerm) {
                            navigationElement.replace(CssUtils.createTerm(navigationElement.getProject(), value));
                        } else if (navigationElement instanceof CssTermList) {
                            navigationElement.replace(CssUtils.createTermList(navigationElement.getProject(), value));
                        } else if (navigationElement instanceof CssDeclaration) {
                            ((CssDeclaration) navigationElement).setValue(value);
                        }
                    } else {
                        // Priority has changed. In this case we need to create a new declaration element and replace the old one.
                        CssDeclaration newDeclaration = CssUtils.createDeclaration(cssDeclaration.getProject(), ".foo", cssDeclaration.getPropertyName(), value, important);
                        cssDeclaration.replace(newDeclaration);
                    }
                }
            }
        } catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    @Override
    public ActionGroup getActionGroup() {
        String groupId = isValid() ? "IncomingChanges.DeclarationNodePopup.Single" : "IncomingChanges.DeclarationNodePopup.Single.Invalid";
        return (ActionGroup) ActionManager.getInstance().getAction(groupId);
    }

    protected CssElement getNavigationElement() {
        if (!isValid()) {
            return null;
        }
        if (CssUtils.isDynamicCssLanguage(cssDeclaration) && CssXFireSettings.getInstance(cssDeclaration.getProject()).isResolveVariables()) {
            PsiElement assignment = CssUtils.resolveVariableAssignment(cssDeclaration);
            if (assignment != null) {
                //noinspection unchecked
                CssElement terms = PsiTreeUtil.getChildOfAnyType(assignment, CssTermList.class, CssTerm.class);
                if (terms != null) {
                    return terms;
                }
            }
        }
        return cssDeclaration;
    }

    public void navigate() {
        CssElement cssElement = getNavigationElement();
        if (cssElement != null) {
            SelectInEditorManager selectInEditorManager = SelectInEditorManager.getInstance(cssElement.getProject());
            VirtualFile virtualFile = cssElement.getContainingFile().getVirtualFile();
            if (virtualFile != null) {
                TextRange textRange = cssElement.getTextRange();
                selectInEditorManager.selectInEditor(virtualFile, textRange.getStartOffset(), textRange.getEndOffset(), false, false);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CssDeclarationNode that = (CssDeclarationNode) o;

        if (!Objects.equals(cssDeclaration, that.cssDeclaration)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = cssDeclaration != null ? cssDeclaration.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void markDeleted() {
        deleted = true;
    }
}
