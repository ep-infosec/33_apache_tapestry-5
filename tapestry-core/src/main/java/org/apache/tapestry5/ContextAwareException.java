// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.apache.tapestry5;

/**
 * An interface implemented by exceptions that get special handling in the default {@link org.apache.tapestry5.services.RequestExceptionHandler}
 * service.
 */
public interface ContextAwareException
{
    /** @return A page activation context for reporting the exception. */
    Object[] getContext();
}
