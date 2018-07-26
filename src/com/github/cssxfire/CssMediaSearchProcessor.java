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

package com.github.cssxfire;

import com.intellij.psi.PsiElement;
import com.intellij.psi.css.CssMediumList;
import com.intellij.psi.search.TextOccurenceProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class CssMediaSearchProcessor implements TextOccurenceProcessor {
    private final Set<CssMediumList> mediaLists = new HashSet<>();
    @NotNull
    private String media;
    @NotNull
    private String word;

    public CssMediaSearchProcessor(@NotNull String media) {
        this.media = StringUtils.normalizeWhitespace(media);
        this.word = StringUtils.extractSearchWord(this.media);
    }

    /**
     * Get the word to use when using {@link com.intellij.psi.search.PsiSearchHelper} to process elements with word
     *
     * @return the word to use in search
     */
    @NotNull
    public String getSearchWord() {
        return word;
    }


    public boolean execute(@NotNull PsiElement element, int offsetInElement) {
        CssMediumList mediumList = CssUtils.findMediumList(element);
        if (mediumList != null) {
            String text = mediumList.getText();
            if (media.equals(StringUtils.normalizeWhitespace(text))) {
                mediaLists.add(mediumList);
            }
        }
        return false;
    }

    public Set<CssMediumList> getMediaLists() {
        return new HashSet<>(mediaLists);
    }
}
