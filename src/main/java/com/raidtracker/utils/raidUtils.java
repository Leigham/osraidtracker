package com.raidtracker.utils;


import com.raidtracker.RaidTracker;
import com.raidtracker.RaidTrackerPlugin;
import org.apache.commons.lang3.ArrayUtils;

import java.lang.reflect.Array;

public class raidUtils
{
    public static void parseRaidTime(String message, RaidTracker raidTracker, RaidStateTracker tracker)
    {
        switch (tracker.getCurrentState().getRaidType())
        {
            case 0 :
                String timeString = message.split("complete! Duration: ")[1];

                String coxRooms[] = {
                        "Upper", "Middle", "Lower", "shamans", "vasa","vanguards","mystics","tekton", "muttadiles", "vespula", "ice demon", "thieving", "tightrope", "crabs"
                };

                for (String room : coxRooms)
                {
                    if (message.startsWith(room))
                    {
                        Array.set(raidTracker.coxTimes, ArrayUtils.indexOf(coxRooms, room), RaidTrackerPlugin.stringTimeToSeconds(timeString.split(" ")[timeString.split(" ").length - 1]));
                        return;
                    };
                    if (message.toLowerCase().contains(room))
                    {
                        Array.set(raidTracker.coxTimes, ArrayUtils.indexOf(coxRooms, room), RaidTrackerPlugin.stringTimeToSeconds(timeString.split(" ")[0]));
                        return;
                    };
                };
                break;
            case 1 :
                if (message.toLowerCase().contains("wave '")) {
                    String wave = message.toLowerCase().split("'")[1];

                    String[] waves = {"the maiden of sugadinti", "the pestilent bloat", "the nylocas", "sotetseg", "xarpus", "the final challenge"};
                    Array.set(
                            raidTracker.tobTimes,
                            ArrayUtils.indexOf(waves, wave),
                            (RaidTrackerPlugin.stringTimeToSeconds(message.toLowerCase().split("duration: ")[1].split((wave.equalsIgnoreCase("the final challenge")) ? "theatre" : "total")[0]
                            )));
                }

                if (message.toLowerCase().contains("theatre of blood wave completion")) {
                    raidTracker.setRaidTime(RaidTrackerPlugin.stringTimeToSeconds(message.toLowerCase().split("time: ")[1].split("personal")[0]));
                }

                break;
            case 2 :
                /*
					Challenge complete: Path of Crondis. Duration: <col=ef1020>1:20</col>. Total: <col=ef1020>1:20</col>
					Challenge complete: Zebak. Duration: <col=ef1020>1:12</col>. Total: <col=ef1020>2:32</col>
			    */
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + tracker.getCurrentState().getRaidType());
        }
    }
}
