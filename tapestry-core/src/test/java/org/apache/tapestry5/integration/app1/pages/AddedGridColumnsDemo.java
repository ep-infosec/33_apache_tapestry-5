// Copyright 2008 The Apache Software Foundation
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

package org.apache.tapestry5.integration.app1.pages;

import org.apache.tapestry5.annotations.Component;
import org.apache.tapestry5.beanmodel.BeanModel;
import org.apache.tapestry5.beanmodel.services.BeanModelSource;
import org.apache.tapestry5.commons.Messages;
import org.apache.tapestry5.corelib.components.Grid;
import org.apache.tapestry5.integration.app1.data.Track;
import org.apache.tapestry5.integration.app1.services.MusicLibrary;
import org.apache.tapestry5.ioc.annotations.Inject;

import java.util.List;

public class AddedGridColumnsDemo
{
    @Component(parameters = {"source=tracks", "row=track", "model=model"})
    private Grid grid;

    @Inject
    private MusicLibrary library;

    private Track track;

    @Inject
    private BeanModelSource source;

    private final BeanModel model;

    @Inject
    private Messages messages;

    {
        model = source.create(Track.class, true, messages);

        model.exclude("album", "artist", "genre", "playcount", "rating");

        model.addEmpty("viewlink");

        model.addExpression("titlelength", "title.length()").label("Title Length");

        // This is to test the case where there's no property conduit or override block.

        model.addEmpty("dummy");
    }

    public Track getTrack()
    {
        return track;
    }

    public void setTrack(Track track)
    {
        this.track = track;
    }

    public List<Track> getTracks()
    {
        return library.getTracks();
    }

    public BeanModel getModel()
    {
        return model;
    }
}
