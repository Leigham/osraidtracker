package com.raidtracker.filereadwriter;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.raidtracker.RaidTracker;
import com.raidtracker.RaidTrackerItem;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static net.runelite.client.RuneLite.RUNELITE_DIR;

@Slf4j
public class FileReadWriter {

    @Getter
    private String username = "Canvasba";
    private String coxDir;
    private String tobDir;
    private String toaDir;


    public void writeToFile(RaidTracker raidTracker)
    {
        String dir;
        if (coxDir == null)
        {
            createFolders();
        };
        switch (raidTracker.getInRaidType())
        {
            case 0 : // chambers;
                dir = coxDir;
                break;
            case 1: // Tob
                dir = tobDir;
                break;
            case 2 :// toa
                dir = toaDir;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + raidTracker.getInRaidType());
        }
        try
        {
            log.info("writer started");

            //use json format so serializing and deserializing is easy
            Gson gson = new GsonBuilder().create();

            JsonParser parser = new JsonParser();
            String fileName = dir + "\\raid_tracker_data.log";

            FileWriter fw = new FileWriter(fileName,true); //the true will append the new data
            gson.toJson(parser.parse(getJSONString(raidTracker, gson, parser)), fw);
            fw.append("\n");

            fw.close();
        }
        catch(IOException ioe)
        {
            System.err.println("IOException: " + ioe.getMessage() + " in writeToFile");
        }
    }

/*
    public String getJSONString(RaidTracker raidTracker, Gson gson, JsonParser parser)
    {
        System.out.println(gson.toJson(raidTracker));
        return gson.toJson(raidTracker);
    };*/
    public String getJSONString(RaidTracker raidTracker, Gson gson, JsonParser parser)
    {
        System.out.println("gson.toJson(raidTracker)");
        System.out.println(gson.toJson(raidTracker));
        JsonObject RTJson =  parser.parse(gson.toJson(raidTracker)).getAsJsonObject();
        List<RaidTrackerItem> lootList = raidTracker.getLootList();
        JsonArray lootListToString = new JsonArray();

        for (RaidTrackerItem item : lootList) {
            lootListToString.add(parser.parse(gson.toJson(item, new TypeToken<RaidTrackerItem>() {
            }.getType())));
        }

        RTJson.addProperty("lootList", lootListToString.toString());
        return RTJson.toString().replace("\\\"", "\"").replace("\"[", "[").replace("]\"", "]");
    }

    public ArrayList<RaidTracker> readFromFile(String alternateFile, int raidType)
    {
        String dir;
        switch (raidType)
        {
            case 0 : // chambers;
                dir = coxDir;
                break;
            case 1: // Tob
                dir = tobDir;
                break;
            case 2 :// toa
                dir = toaDir;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + raidType);
        }
        String fileName;
        if (alternateFile.length() != 0) {
            fileName = alternateFile;
        }
        else {
            fileName = dir + "\\raid_tracker_data.log";
        }

        try {
            Gson gson = new GsonBuilder().create();
            JsonParser parser = new JsonParser();

            BufferedReader bufferedreader = new BufferedReader(new FileReader(fileName));
            String line;

            ArrayList<RaidTracker> RTList = new ArrayList<>();
            while ((line = bufferedreader.readLine()) != null && line.length() > 0) {
                try {
                    RaidTracker parsed = gson.fromJson(parser.parse(line), RaidTracker.class);
                    RTList.add(parsed);
                }
                catch (JsonSyntaxException e) {
                    System.out.println("Bad line: " + line);
                }

            }

            bufferedreader.close();
            return RTList;
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public ArrayList<RaidTracker> readFromFile(int type) {
        return readFromFile("", type);

    }

    public void createFolders()
    {
        System.out.println("creating folders with name "+ username);
        File dir = new File(RUNELITE_DIR, "raid-data tracker");
        IGNORE_RESULT(dir.mkdir());
        dir = new File(dir, username);
        IGNORE_RESULT(dir.mkdir());
        File dir_cox = new File(dir, "cox");
        File dir_tob = new File(dir, "tob");
        File dir_toa   = new File(dir, "toa");
        IGNORE_RESULT(dir_cox.mkdir());
        IGNORE_RESULT(dir_tob.mkdir());
        IGNORE_RESULT(dir_toa.mkdir());
        File newCoxFile = new File(dir_cox + "\\raid_tracker_data.log");
        File newTobFile = new File(dir_tob + "\\raid_tracker_data.log");
        File newToaFile = new File(dir_toa + "\\raid_tracker_data.log");

        try {
            IGNORE_RESULT(newCoxFile.createNewFile());
            IGNORE_RESULT(newTobFile.createNewFile());
            IGNORE_RESULT(newToaFile.createNewFile());
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.coxDir = dir_cox.getAbsolutePath();
        this.tobDir = dir_tob.getAbsolutePath();
        this.toaDir = dir_toa.getAbsolutePath();
    }

    public void updateUsername(final String username) {
        this.username = username;
        createFolders();
    }

    public void updateRTList(ArrayList<RaidTracker> RTList, int type) {
        String dir;

        switch (type)
        {
            case 0 : // chambers;
                dir = coxDir;
                break;
            case 1: // Tob
                dir = tobDir;
                break;
            case 2 :// toa
                dir = toaDir;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + type);
        };

        try {
            Gson gson = new GsonBuilder().create();

            JsonParser parser = new JsonParser();
            String fileName = dir + "\\raid_tracker_data.log";


            FileWriter fw = new FileWriter(fileName, false); //the true will append the new data

            for (RaidTracker RT : RTList) {
                if (RT.getLootSplitPaid() > 0) {
                    RT.setSpecialLootInOwnName(true);
                }
                else {
                    //bit of a wonky check, so try to avoid with lootsplitpaid if possible
                    RT.setSpecialLootInOwnName(RT.getLootList().size() > 0 && RT.getLootList().get(0).getName().equalsIgnoreCase(RT.getSpecialLoot()));
                }

                gson.toJson(parser.parse(getJSONString(RT, gson, parser)), fw);

                fw.append("\n");
            }

            fw.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public boolean delete(int type) {
        String dir;

        switch (type)
        {
            case 0 : // chambers;
                dir = coxDir;
                break;
            case 1: // Tob
                dir = tobDir;
                break;
            case 2 :// toa
                dir = toaDir;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + type);
        };

        File newFile = new File(dir + "\\raid_tracker_data.log");

        boolean isDeleted = newFile.delete();

        try {
            IGNORE_RESULT(newFile.createNewFile());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return isDeleted;
    }

    public void IGNORE_RESULT(boolean b) {}
}
