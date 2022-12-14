// Copyright 2006-2014 The Apache Software Foundation
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

package org.apache.tapestry5.ioc.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.tapestry5.commons.Configuration;
import org.apache.tapestry5.commons.MappedConfiguration;
import org.apache.tapestry5.commons.ObjectCreator;
import org.apache.tapestry5.commons.OrderedConfiguration;
import org.apache.tapestry5.commons.internal.util.TapestryException;
import org.apache.tapestry5.commons.services.PlasticProxyFactory;
import org.apache.tapestry5.commons.util.CollectionFactory;
import org.apache.tapestry5.func.F;
import org.apache.tapestry5.func.Mapper;
import org.apache.tapestry5.func.Predicate;
import org.apache.tapestry5.ioc.AdvisorDef;
import org.apache.tapestry5.ioc.MethodAdviceReceiver;
import org.apache.tapestry5.ioc.ScopeConstants;
import org.apache.tapestry5.ioc.ServiceBinder;
import org.apache.tapestry5.ioc.ServiceBuilderResources;
import org.apache.tapestry5.ioc.annotations.Advise;
import org.apache.tapestry5.ioc.annotations.Contribute;
import org.apache.tapestry5.ioc.annotations.Decorate;
import org.apache.tapestry5.ioc.annotations.EagerLoad;
import org.apache.tapestry5.ioc.annotations.Marker;
import org.apache.tapestry5.ioc.annotations.Match;
import org.apache.tapestry5.ioc.annotations.Optional;
import org.apache.tapestry5.ioc.annotations.Order;
import org.apache.tapestry5.ioc.annotations.PreventServiceDecoration;
import org.apache.tapestry5.ioc.annotations.Scope;
import org.apache.tapestry5.ioc.annotations.Startup;
import org.apache.tapestry5.ioc.def.ContributionDef;
import org.apache.tapestry5.ioc.def.ContributionDef3;
import org.apache.tapestry5.ioc.def.DecoratorDef;
import org.apache.tapestry5.ioc.def.ModuleDef2;
import org.apache.tapestry5.ioc.def.ServiceDef;
import org.apache.tapestry5.ioc.def.StartupDef;
import org.apache.tapestry5.ioc.internal.util.InternalUtils;
import org.slf4j.Logger;

/**
 * Starting from the Class for a module, identifies all the services (service builder methods),
 * decorators (service
 * decorator methods) and (not yet implemented) contributions (service contributor methods).
 */
public class DefaultModuleDefImpl implements ModuleDef2, ServiceDefAccumulator
{
    /**
     * The prefix used to identify service builder methods.
     */
    private static final String BUILD_METHOD_NAME_PREFIX = "build";

    /**
     * The prefix used to identify service decorator methods.
     */
    private static final String DECORATE_METHOD_NAME_PREFIX = "decorate";

    /**
     * The prefix used to identify service contribution methods.
     */
    private static final String CONTRIBUTE_METHOD_NAME_PREFIX = "contribute";

    private static final String ADVISE_METHOD_NAME_PREFIX = "advise";

    private final static Map<Class, ConfigurationType> PARAMETER_TYPE_TO_CONFIGURATION_TYPE = CollectionFactory
            .newMap();

    private final Class moduleClass;

    private final Logger logger;

    private final PlasticProxyFactory proxyFactory;

    /**
     * Keyed on service id.
     */
    private final Map<String, ServiceDef> serviceDefs = CollectionFactory.newCaseInsensitiveMap();

    /**
     * Keyed on decorator id.
     */
    private final Map<String, DecoratorDef> decoratorDefs = CollectionFactory.newCaseInsensitiveMap();

    private final Map<String, AdvisorDef> advisorDefs = CollectionFactory.newCaseInsensitiveMap();

    private final Set<ContributionDef> contributionDefs = CollectionFactory.newSet();

    private final Set<Class> defaultMarkers = CollectionFactory.newSet();

    private final Set<StartupDef> startups = CollectionFactory.newSet();

    private final static Set<Method> OBJECT_METHODS = CollectionFactory.newSet(Object.class.getMethods());

    static
    {
        PARAMETER_TYPE_TO_CONFIGURATION_TYPE.put(Configuration.class, ConfigurationType.UNORDERED);
        PARAMETER_TYPE_TO_CONFIGURATION_TYPE.put(OrderedConfiguration.class, ConfigurationType.ORDERED);
        PARAMETER_TYPE_TO_CONFIGURATION_TYPE.put(MappedConfiguration.class, ConfigurationType.MAPPED);
    }

    /**
     * @param moduleClass
     *         the class that is responsible for building services, etc.
     * @param logger
     *         based on the class name of the module
     * @param proxyFactory
     *         factory used to create proxy classes at runtime
     */
    public DefaultModuleDefImpl(Class<?> moduleClass, Logger logger, PlasticProxyFactory proxyFactory)
    {
        this.moduleClass = moduleClass;
        this.logger = logger;
        this.proxyFactory = proxyFactory;

        Marker annotation = moduleClass.getAnnotation(Marker.class);

        if (annotation != null)
        {
            InternalUtils.validateMarkerAnnotations(annotation.value());
            defaultMarkers.addAll(Arrays.asList(annotation.value()));
        }

        // Want to verify that every public method is meaningful to Tapestry IoC. Remaining methods
        // might
        // have typos, i.e., "createFoo" that should be "buildFoo".

        Set<Method> methods;
        try 
        {
            methods = CollectionFactory.newSet(moduleClass.getMethods());
        }
        catch (Exception e)
        {
            throw new TapestryException(
                    "Exception while processing module class " + moduleClass.getName() +
                    ": " + e.getMessage(), e);
        }

        Iterator<Method> methodIterator = methods.iterator();

        while (methodIterator.hasNext())
        {
            Method method = methodIterator.next();
            for (Method objectMethod : OBJECT_METHODS)
            {
                if (signaturesAreEqual(method, objectMethod))
                {
                    methodIterator.remove();
                    break;
                }
            }
        }

        removeSyntheticMethods(methods);
        removeGroovyObjectMethods(methods);

        boolean modulePreventsServiceDecoration = moduleClass.getAnnotation(PreventServiceDecoration.class) != null;

        grind(methods, modulePreventsServiceDecoration);
        bind(methods, modulePreventsServiceDecoration);

        if (methods.isEmpty())
            return;

        throw new RuntimeException(String.format("Module class %s contains unrecognized public methods: %s.",
                moduleClass.getName(), InternalUtils.joinSorted(methods)));
    }

    private static boolean signaturesAreEqual(Method m1, Method m2)
    {
        if (m1.getName() == m2.getName()) {
            if (!m1.getReturnType().equals(m2.getReturnType()))
                return false;
            Class<?>[] params1 = m1.getParameterTypes();
            Class<?>[] params2 = m2.getParameterTypes();
            if (params1.length == params2.length)
            {
                for (int i = 0; i < params1.length; i++) {
                    if (params1[i] != params2[i])
                        return false;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Identifies the module class and a list of service ids within the module.
     */
    @Override
    public String toString()
    {
        return String.format("ModuleDef[%s %s]", moduleClass.getName(), InternalUtils.joinSorted(serviceDefs.keySet()));
    }

    @Override
    public Class getBuilderClass()
    {
        return moduleClass;
    }

    @Override
    public Set<String> getServiceIds()
    {
        return serviceDefs.keySet();
    }

    @Override
    public ServiceDef getServiceDef(String serviceId)
    {
        return serviceDefs.get(serviceId);
    }
    
    private void removeGroovyObjectMethods(Set<Method> methods)
    {
        Iterator<Method> iterator = methods.iterator();

        while (iterator.hasNext())
        {
            Method m = iterator.next();
            final String name = m.getName();

            if (m.getDeclaringClass().getName().equals("groovy.lang.GroovyObject")
                    || (name.equals("getMetaClass") && m.getReturnType().getName().equals("groovy.lang.MetaClass"))
                || (m.getParameterCount() == 1 && m.getParameterTypes()[0].getName().equals("groovy.lang.MetaClass")))
            {
                iterator.remove();
            }
        }
    }

    private void removeSyntheticMethods(Set<Method> methods)
    {
        Iterator<Method> iterator = methods.iterator();

        while (iterator.hasNext())
        {
            Method m = iterator.next();

            if (m.isSynthetic() || m.getName().startsWith("$"))
            {
                iterator.remove();
            }
        }
    }

    private void grind(Set<Method> remainingMethods, boolean modulePreventsServiceDecoration)
    {
        Method[] methods = moduleClass.getMethods();

        Comparator<Method> c = new Comparator<Method>()
        {
            // By name, ascending, then by parameter count, descending.

            @Override
            public int compare(Method o1, Method o2)
            {
                int result = o1.getName().compareTo(o2.getName());

                if (result == 0)
                    result = o2.getParameterTypes().length - o1.getParameterTypes().length;

                return result;
            }
        };

        Arrays.sort(methods, c);

        for (Method m : methods)
        {
            String name = m.getName();

            if (name.startsWith(BUILD_METHOD_NAME_PREFIX))
            {
                addServiceDef(m, modulePreventsServiceDecoration);
                remainingMethods.remove(m);
                continue;
            }

            if (name.startsWith(DECORATE_METHOD_NAME_PREFIX) || m.isAnnotationPresent(Decorate.class))
            {
                addDecoratorDef(m);
                remainingMethods.remove(m);
                continue;
            }

            if (name.startsWith(CONTRIBUTE_METHOD_NAME_PREFIX) || m.isAnnotationPresent(Contribute.class))
            {
                addContributionDef(m);
                remainingMethods.remove(m);
                continue;
            }

            if (name.startsWith(ADVISE_METHOD_NAME_PREFIX) || m.isAnnotationPresent(Advise.class))
            {
                addAdvisorDef(m);
                remainingMethods.remove(m);
                continue;
            }

            if (m.isAnnotationPresent(Startup.class))
            {
                addStartupDef(m);
                remainingMethods.remove(m);
                continue;
            }
        }
    }

    private void addStartupDef(Method method)
    {
        startups.add(new StartupDefImpl(method));
    }

    private void addContributionDef(Method method)
    {
        Contribute annotation = method.getAnnotation(Contribute.class);

        Class serviceInterface = annotation == null ? null : annotation.value();

        String serviceId = annotation != null ? null : stripMethodPrefix(method, CONTRIBUTE_METHOD_NAME_PREFIX);

        Class returnType = method.getReturnType();
        if (!returnType.equals(void.class))
            logger.warn(IOCMessages.contributionWrongReturnType(method));

        ConfigurationType type = null;

        for (Class parameterType : method.getParameterTypes())
        {
            ConfigurationType thisParameter = PARAMETER_TYPE_TO_CONFIGURATION_TYPE.get(parameterType);

            if (thisParameter != null)
            {
                if (type != null)
                    throw new RuntimeException(IOCMessages.tooManyContributionParameters(method));

                type = thisParameter;
            }
        }

        if (type == null)
            throw new RuntimeException(IOCMessages.noContributionParameter(method));

        Set<Class> markers = extractMarkers(method, Contribute.class, Optional.class);

        boolean optional = method.getAnnotation(Optional.class) != null;

        ContributionDef3 def = new ContributionDefImpl(serviceId, method, optional, proxyFactory, serviceInterface, markers);

        contributionDefs.add(def);
    }

    private void addDecoratorDef(Method method)
    {
        Decorate annotation = method.getAnnotation(Decorate.class);

        Class serviceInterface = annotation == null ? null : annotation.serviceInterface();

        // TODO: methods just named "decorate"

        String decoratorId = annotation == null ? stripMethodPrefix(method, DECORATE_METHOD_NAME_PREFIX) : extractId(
                serviceInterface, annotation.id());

        // TODO: Check for duplicates

        Class returnType = method.getReturnType();

        if (returnType.isPrimitive() || returnType.isArray())
        {
            throw new RuntimeException(String.format(
                    "Method %s is named like a service decorator method, but the return type (%s) is not acceptable (try Object).",
                    InternalUtils.asString(method),
                    method.getReturnType().getCanonicalName()));
        }


        Set<Class> markers = extractMarkers(method, Decorate.class);

        DecoratorDef def = new DecoratorDefImpl(method, extractPatterns(decoratorId, method),
                extractConstraints(method), proxyFactory, decoratorId, serviceInterface, markers);

        decoratorDefs.put(decoratorId, def);
    }

    private <T extends Annotation> String[] extractPatterns(String id, Method method)
    {
        Match match = method.getAnnotation(Match.class);

        if (match == null)
        {
            return new String[]{id};
        }

        return match.value();
    }

    private String[] extractConstraints(Method method)
    {
        Order order = method.getAnnotation(Order.class);

        if (order == null)
            return null;

        return order.value();
    }

    private void addAdvisorDef(Method method)
    {
        Advise annotation = method.getAnnotation(Advise.class);

        Class serviceInterface = annotation == null ? null : annotation.serviceInterface();

        // TODO: methods just named "decorate"

        String advisorId = annotation == null ? stripMethodPrefix(method, ADVISE_METHOD_NAME_PREFIX) : extractId(
                serviceInterface, annotation.id());

        // TODO: Check for duplicates

        Class returnType = method.getReturnType();

        if (!returnType.equals(void.class))
            throw new RuntimeException(String.format("Advise method %s does not return void.", toString(method)));

        boolean found = false;

        for (Class pt : method.getParameterTypes())
        {
            if (pt.equals(MethodAdviceReceiver.class))
            {
                found = true;

                break;
            }
        }

        if (!found)
            throw new RuntimeException(String.format("Advise method %s must take a parameter of type %s.",
                    toString(method), MethodAdviceReceiver.class.getName()));

        Set<Class> markers = extractMarkers(method, Advise.class);

        AdvisorDef def = new AdvisorDefImpl(method, extractPatterns(advisorId, method),
                extractConstraints(method), proxyFactory, advisorId, serviceInterface, markers);

        advisorDefs.put(advisorId, def);

    }

    private String extractId(Class serviceInterface, String id)
    {
        return InternalUtils.isBlank(id) ? serviceInterface.getSimpleName() : id;
    }

    private String toString(Method method)
    {
        return InternalUtils.asString(method, proxyFactory);
    }

    private String stripMethodPrefix(Method method, String prefix)
    {
        return method.getName().substring(prefix.length());
    }

    /**
     * Invoked for public methods that have the proper prefix.
     */
    private void addServiceDef(final Method method, boolean modulePreventsServiceDecoration)
    {
        String serviceId = InternalUtils.getServiceId(method);

        if (serviceId == null)
        {
            serviceId = stripMethodPrefix(method, BUILD_METHOD_NAME_PREFIX);
        }

        // If the method name was just "build()", then work from the return type.

        if (serviceId.equals(""))
            serviceId = method.getReturnType().getSimpleName();

        // Any number of parameters is fine, we'll adapt. Eventually we have to check
        // that we can satisfy the parameters requested. Thrown exceptions of the method
        // will be caught and wrapped, so we don't need to check those. But we do need a proper
        // return type.

        Class returnType = method.getReturnType();

        if (returnType.isPrimitive() || returnType.isArray())
            throw new RuntimeException(
                    String.format("Method %s is named like a service builder method, but the return type (%s) is not acceptable (try an interface).",
                            InternalUtils.asString(method),
                            method.getReturnType().getCanonicalName()));

        String scope = extractServiceScope(method);
        boolean eagerLoad = method.isAnnotationPresent(EagerLoad.class);

        boolean preventDecoration = modulePreventsServiceDecoration
                || method.getAnnotation(PreventServiceDecoration.class) != null;

        ObjectCreatorSource source = new ObjectCreatorSource()
        {
            @Override
            public ObjectCreator constructCreator(ServiceBuilderResources resources)
            {
                return new ServiceBuilderMethodInvoker(resources, getDescription(), method);
            }

            @Override
            public String getDescription()
            {
                return DefaultModuleDefImpl.this.toString(method);
            }
        };

        Set<Class> markers = CollectionFactory.newSet(defaultMarkers);
        markers.addAll(extractServiceDefMarkers(method));

        ServiceDefImpl serviceDef = new ServiceDefImpl(returnType, null, serviceId, markers, scope, eagerLoad,
                preventDecoration, source);

        addServiceDef(serviceDef);
    }

    private Collection<Class> extractServiceDefMarkers(Method method)
    {
        Marker annotation = method.getAnnotation(Marker.class);

        if (annotation == null)
            return Collections.emptyList();

        return CollectionFactory.newList(annotation.value());
    }

    @SuppressWarnings("rawtypes")
    private Set<Class> extractMarkers(Method method, final Class... annotationClassesToSkip)
    {
        return F.flow(method.getAnnotations()).map(new Mapper<Annotation, Class>()
        {
            @Override
            public Class map(Annotation value)
            {
                return value.annotationType();
            }
        }).filter(new Predicate<Class>()
        {
            @Override
            public boolean accept(Class element)
            {
                for (Class skip : annotationClassesToSkip)
                {
                    if (skip.equals(element))
                    {
                        return false;
                    }
                }

                return true;
            }
        }).toSet();
    }

    @Override
    public void addServiceDef(ServiceDef serviceDef)
    {
        String serviceId = serviceDef.getServiceId();

        ServiceDef existing = serviceDefs.get(serviceId);

        if (existing != null)
            throw new RuntimeException(IOCMessages.buildMethodConflict(serviceId, serviceDef.toString(),
                    existing.toString()));

        serviceDefs.put(serviceId, serviceDef);
    }

    private String extractServiceScope(Method method)
    {
        Scope scope = method.getAnnotation(Scope.class);

        return scope != null ? scope.value() : ScopeConstants.DEFAULT;
    }

    @Override
    public Set<DecoratorDef> getDecoratorDefs()
    {
        return toSet(decoratorDefs);
    }

    @Override
    public Set<ContributionDef> getContributionDefs()
    {
        return contributionDefs;
    }

    @Override
    public String getLoggerName()
    {
        return moduleClass.getName();
    }

    /**
     * See if the build class defined a bind method and invoke it.
     *
     * @param remainingMethods
     *         set of methods as yet unaccounted for
     * @param modulePreventsServiceDecoration
     *         true if {@link org.apache.tapestry5.ioc.annotations.PreventServiceDecoration} on
     *         module
     *         class
     */
    private void bind(Set<Method> remainingMethods, boolean modulePreventsServiceDecoration)
    {
        Throwable failure;
        Method bindMethod = null;

        try
        {
            bindMethod = moduleClass.getMethod("bind", ServiceBinder.class);

            if (!Modifier.isStatic(bindMethod.getModifiers()))
                throw new RuntimeException(IOCMessages.bindMethodMustBeStatic(toString(bindMethod)));

            ServiceBinderImpl binder = new ServiceBinderImpl(this, bindMethod, proxyFactory, defaultMarkers,
                    modulePreventsServiceDecoration);

            bindMethod.invoke(null, binder);

            binder.finish();

            remainingMethods.remove(bindMethod);

            return;
        } catch (NoSuchMethodException ex)
        {
            // No problem! Many modules will not have such a method.

            return;
        } catch (IllegalArgumentException ex)
        {
            failure = ex;
        } catch (IllegalAccessException ex)
        {
            failure = ex;
        } catch (InvocationTargetException ex)
        {
            failure = ex.getTargetException();
        }

        String methodId = toString(bindMethod);

        throw new RuntimeException(IOCMessages.errorInBindMethod(methodId, failure), failure);
    }

    @Override
    public Set<AdvisorDef> getAdvisorDefs()
    {
        return toSet(advisorDefs);
    }

    private <K, V> Set<V> toSet(Map<K, V> map)
    {
        return CollectionFactory.newSet(map.values());
    }

    @Override
    public Set<StartupDef> getStartups()
    {
        return startups;
    }
}
