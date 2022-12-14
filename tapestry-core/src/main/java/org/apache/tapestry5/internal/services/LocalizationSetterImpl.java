// Copyright 2006, 2007, 2009, 2010, 2012 The Apache Software Foundation
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

package org.apache.tapestry5.internal.services;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.tapestry5.OptionModel;
import org.apache.tapestry5.SelectModel;
import org.apache.tapestry5.SymbolConstants;
import org.apache.tapestry5.commons.util.CollectionFactory;
import org.apache.tapestry5.http.services.Request;
import org.apache.tapestry5.internal.OptionModelImpl;
import org.apache.tapestry5.internal.SelectModelImpl;
import org.apache.tapestry5.internal.TapestryInternalUtils;
import org.apache.tapestry5.ioc.annotations.Symbol;
import org.apache.tapestry5.ioc.services.ThreadLocale;
import org.apache.tapestry5.services.LocalizationSetter;
import org.apache.tapestry5.services.PersistentLocale;

/**
 * Given a set of supported locales, for a specified desired locale, sets the current thread's locale to a supported
 * locale that is closest to the desired.
 */
public class LocalizationSetterImpl implements LocalizationSetter
{
    private final Request request;

    private final ThreadLocale threadLocale;

    private final Locale defaultLocale;

    private final Set<String> supportedLocaleNames;

    private final List<Locale> supportedLocales;

    private final Map<String, Locale> localeCache = CollectionFactory.newConcurrentMap();

    private final PersistentLocale persistentLocale;

    public LocalizationSetterImpl(Request request,

    PersistentLocale persistentLocale,

    ThreadLocale threadLocale,

    @Symbol(SymbolConstants.SUPPORTED_LOCALES)
    String localeNames)
    {
        this.request = request;

        this.persistentLocale = persistentLocale;
        this.threadLocale = threadLocale;

        this.supportedLocaleNames = CollectionFactory.newSet();
        
        String[] names = TapestryInternalUtils.splitAtCommas(localeNames);
        
        for (String name : names)
        {
            supportedLocaleNames.add(name.toLowerCase());
        }

        supportedLocales = parseNames(names);

        defaultLocale = supportedLocales.get(0);
    }

    private List<Locale> parseNames(String[] localeNames)
    {
        List<Locale> list = CollectionFactory.newList();

        for (String name : localeNames)
        {
            list.add(toLocale(name));
        }

        return Collections.unmodifiableList(list);
    }

    public Locale toLocale(String localeName)
    {
        Locale result = localeCache.get(localeName);

        if (result == null)
        {
            result = constructLocale(localeName);
            localeCache.put(localeName, result);
        }

        return result;
    }

    private Locale constructLocale(String name)
    {
        String[] terms = name.split("_");

        switch (terms.length)
        {
            case 1:
                return new Locale(terms[0], "");

            case 2:
                return new Locale(terms[0], terms[1]);

            case 3:

                return new Locale(terms[0], terms[1], terms[2]);

            default:

                throw new IllegalArgumentException();
        }
    }

    public boolean setLocaleFromLocaleName(String localeName)
    {
        boolean supported = isSupportedLocaleName(localeName);

        if (supported)
        {
            Locale locale = findClosestSupportedLocale(toLocale(localeName));

            persistentLocale.set(locale);

            threadLocale.setLocale(locale);
        }
        else
        {
            Locale requestLocale = request.getLocale();

            Locale supportedLocale = findClosestSupportedLocale(requestLocale);

            threadLocale.setLocale(supportedLocale);
        }

        return supported;
    }

    public void setNonPersistentLocaleFromLocaleName(String localeName)
    {
        Locale requested = toLocale(localeName);

        Locale supported = findClosestSupportedLocale(requested);

        threadLocale.setLocale(supported);
    }
    
    public void setNonPersistentLocaleFromRequest(Request request)
    {
        Locale locale = request.getLocale();
        setNonPersistentLocaleFromLocaleName(locale.toString());
    }

    private Locale findClosestSupportedLocale(Locale desiredLocale)
    {
        String localeName = desiredLocale.toString();

        while (true)
        {
            if (isSupportedLocaleName(localeName))
                return toLocale(localeName);

            localeName = stripTerm(localeName);

            if (localeName.length() == 0)
                break;
        }

        return defaultLocale;
    }

    static String stripTerm(String localeName)
    {
        int scorex = localeName.lastIndexOf('_');

        return scorex < 0 ? "" : localeName.substring(0, scorex);
    }

    public List<Locale> getSupportedLocales()
    {
        return supportedLocales;
    }

    public boolean isSupportedLocaleName(String localeName)
    {
        return supportedLocaleNames.contains(localeName.toLowerCase());
    }

    public SelectModel getSupportedLocalesModel()
    {
        List<OptionModel> options = CollectionFactory.newList();

        for (Locale l : supportedLocales)
        {
            options.add(new OptionModelImpl(l.getDisplayName(l), l));
        }

        return new SelectModelImpl(null, options);
    }

}
