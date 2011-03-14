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

package com.googlecode.cssxfire.action;

import com.googlecode.cssxfire.ui.Icons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Ronnie
 */
public abstract class BooleanOption extends AbstractIncomingChangesAction
{
    protected abstract boolean getOptionValue(AnActionEvent event);

    protected abstract void setOptionValue(AnActionEvent event, boolean value);

    @NotNull
    protected abstract String getOptionName();

    @Override
    public final void update(AnActionEvent event)
    {
        // Set "check" icon if option is active
        event.getPresentation().setIcon(getOptionValue(event) ? Icons.CHECK : null);
    }

    @Override
    public final void actionPerformed(AnActionEvent event)
    {
        // Flip value
        setOptionValue(event, !getOptionValue(event));
    }
}
