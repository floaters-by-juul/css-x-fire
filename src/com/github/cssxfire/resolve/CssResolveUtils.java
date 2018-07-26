/*
 * Copyright 2012 Ronnie Kolehmainen
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

package com.github.cssxfire.resolve;

import com.github.cssxfire.CssUtils;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveState;
import com.intellij.psi.css.CssImport;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;

/**
 * <p>
 *     Resolving of a variable (or mixin) is performed in the following manner:
 *     <ol>
 *         <li>Process all declarations in current file.</li>
 *         <li>Process all files imported by current file, and their imports until there are no more imports.</li>
 *         <li>Search for files that are importing current file and repeat from step 1 (with <i>current</i> file being the <i>importing file</i>).</li>
 *     </ol>
 *     As soon as the declaration is found the processing is stopped.
 * </p>
 */
public class CssResolveUtils {
    private static final Key<Collection<String>> PROCESSED_PATHS = new Key<>("PROCESSED_PATHS");

    /**
     * Checks if the PSI tree in given root contains a PsiErrorElement.
     * @param root the PSI tree to check
     * @return <tt>true</tt> if there is at least one error element in the given tree
     */
    public static boolean containsErrors(@Nullable PsiElement root) {
        return root != null && PsiTreeUtil.findChildOfType(root, PsiErrorElement.class) != null;
    }

    @Nullable
    public static PsiElement resolveVariable(@NotNull PsiElement base, @NotNull String name) {
        CssResolveProcessor processor = CssPluginsFacade.getVariableProcessor(base, name);
        if (processor.executeInScope(base)) {
            processFile(base.getContainingFile(), processor, ResolveState.initial().put(PROCESSED_PATHS, new HashSet<>()));
        }
        return processor.getResult();
    }
    
    @Nullable
    public static PsiElement resolveMixin(@NotNull PsiElement base, @NotNull String name) {
        CssResolveProcessor processor = CssPluginsFacade.getMixinProcessor(base, name);
        if (processor.executeInScope(base)) {
            processFile(base.getContainingFile(), processor, ResolveState.initial().put(PROCESSED_PATHS, new HashSet<>()));
        }
        return processor.getResult();
    }
    
    private static boolean processFile(final PsiFile file, final CssResolveProcessor processor, final ResolveState state) {
        if (!pushPath(file, state)) {
            // Already visited, skip
            return true;
        }

        // Process declarations in file
        if (!PsiTreeUtil.processElements(file, processor)) {
            return false;
        }

        // Process imports in file
        CssImport cssImport;
        while ((cssImport = processor.popImport()) != null) {
            PsiFile[] imports = cssImport.resolve();
            for (PsiFile resolvedImport : imports) {
                if (!processFile(resolvedImport, processor, state)) {
                    return false;
                }
            }
        }

        // Recurse on files importing this file
        CssUtils.getPsiSearchHelper(file.getProject()).processElementsWithWord((element, offsetInElement) -> {
            if (element.getParent().getParent() instanceof CssImport) {
                CssImport cssImport1 = (CssImport) element.getParent().getParent();
                String[] uris = cssImport1.getUriStrings();
                for (String uri : uris) {
                    if (uri != null && uri.endsWith(file.getName())) {
                        PsiFile[] imports = cssImport1.resolve();
                        for (PsiFile importedFile : imports) {
                            if (file == importedFile) {
                                if (!processFile(cssImport1.getContainingFile(), processor, state)) {
                                    return false;
                                }
                            }
                        }
                    }
                }
            }
            return true;
        }, getResolveSearchScope(file), file.getName(), (short) (UsageSearchContext.IN_CODE | UsageSearchContext.IN_STRINGS), true);

        return true;
    }

    @NotNull
    private static SearchScope getResolveSearchScope(@NotNull PsiFile file) {
        return GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.projectScope(file.getProject()), file.getFileType());
    }

    private static final Object LOCK = new Object();

    private static boolean pushPath(@NotNull PsiFile file, @NotNull ResolveState state) {
        synchronized (LOCK) {
            if (!CssUtils.isDynamicCssLanguage(file)) {
                return false;
            }
            Collection<String> paths = state.get(PROCESSED_PATHS);
            if (paths == null) {
                return false;
            }
            VirtualFile virtualFile = file.getVirtualFile();
            return virtualFile != null && paths.add(virtualFile.getPath());
        }
    }
}
