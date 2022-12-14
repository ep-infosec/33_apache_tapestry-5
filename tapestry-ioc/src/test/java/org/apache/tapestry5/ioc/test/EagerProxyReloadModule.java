// Copyright 2010, 2012 The Apache Software Foundation
//
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

package org.apache.tapestry5.ioc.test;

import org.apache.tapestry5.ioc.ServiceBinder;

//@ImportModule(EagerProxy2ReloadModule.class)
public class EagerProxyReloadModule
{
    public static boolean eagerLoadService1DidLoad;
    public static boolean nonProxyEagerLoadServiceDidLoad;
    public static boolean eagerLoadService2DidLoad;

    public static void bind(ServiceBinder binder)
    {
        binder.bind(EagerLoadService1.class, EagerLoadService1Impl.class);
        binder.bind(NonProxiedEagerLoadService.class).eagerLoad();
        binder.bind(EagerLoadService2.class, EagerLoadService2Impl.class).eagerLoad();
    }
}
