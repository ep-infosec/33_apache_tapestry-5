// Copyright 2008, 2009, 2022 The Apache Software Foundation
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

package org.apache.tapestry5.annotations;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.FIELD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

import static org.apache.tapestry5.ioc.annotations.AnnotationUseContext.*;
import org.apache.tapestry5.ioc.annotations.UseWith;

/**
 * Allows components defined in the template to be injected as read-only properties.
 */
@Target(FIELD)
@Documented
@Retention(RUNTIME)
@UseWith({COMPONENT,PAGE})
public @interface InjectComponent
{
    /**
     * The name of the component to inject. Defaults to the name of the annotated field.
     */
    String value() default "";

    /**
     * If true, allows the component to be missing without throwing an exception.
     * @since 5.8.2
     */
    boolean optional() default false;
}
