package com.raidtracker;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;


@RunWith(MockitoJUnitRunner.class)
public class GenericTests
{
    @Inject
    public Gson gson;

    @Before
    public void before()
    {
        Gson gson = new Gson();
        Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    };
    @Test
    public void TestJsonToString()
    {
        ArrayList<RaidTrackerItem> lootList = new ArrayList<>();
        lootList.add(new RaidTrackerItem("item", 1, 1, 1));
        lootList.add(new RaidTrackerItem("item", 1, 1, 1));
        lootList.add(new RaidTrackerItem("item", 1, 1, 1));
        System.out.println(gson.toJson(lootList));
    }
    @Test
    public void TestJsonToStringWithTracker()
    {
        RaidTracker raidTracker = new RaidTracker();
        ArrayList<RaidTrackerItem> lootList = new ArrayList<>();
        lootList.add(new RaidTrackerItem("item", 1, 1, 1));
        lootList.add(new RaidTrackerItem("item", 1, 1, 1));
        lootList.add(new RaidTrackerItem("item", 1, 1, 1));
        raidTracker.setLootList(lootList);
        System.out.println("here: " + gson.toJson(raidTracker.lootList));
    }

}
