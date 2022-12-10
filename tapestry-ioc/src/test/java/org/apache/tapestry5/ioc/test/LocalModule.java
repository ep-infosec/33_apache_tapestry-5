//  Copyright 2008 The Apache Software Foundation
//
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

package org.apache.tapestry5.ioc.test;

import org.apache.tapestry5.ioc.annotations.Local;

public class LocalModule
{
    public Greeter buildDrawlGreeter()
    {
        return new Greeter()
        {
            @Override
            public String getGreeting()
            {
                return "Hello, y'all!";
            }
        };
    }

    public StringHolder buildLocalGreeterHolder(final @Local Greeter greeter)
    {
        return new StringHolder()
        {
            @Override
            public void setValue(String value)
            {
            }

            @Override
            public String getValue()
            {
                return greeter.getGreeting();
            }
        };
    }
}
