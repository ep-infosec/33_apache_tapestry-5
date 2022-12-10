// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.example.upload.pages;

import org.apache.tapestry5.Block;
import org.apache.tapestry5.ClientElement;
import org.apache.tapestry5.annotations.Environmental;
import org.apache.tapestry5.annotations.InjectComponent;
import org.apache.tapestry5.ioc.annotations.Inject;
import org.apache.tapestry5.services.javascript.JavaScriptSupport;
import org.example.upload.base.UploadBasePage;

public class Ajaxified extends UploadBasePage
{
    @Inject
    private Block content;

    @InjectComponent
    private ClientElement injector;

    @Environmental
    private JavaScriptSupport javaScriptSupport;

    Object onActionFromInjector()
    {
        return content;
    }

    void afterRender()
    {
        javaScriptSupport.addScript(
                "$('trigger').observe('click', function(event) { $('%s').trigger(); Event.stop(event); });",
                injector.getClientId());
    }
}
