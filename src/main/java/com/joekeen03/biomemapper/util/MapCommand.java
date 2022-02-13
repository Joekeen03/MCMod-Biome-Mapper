package com.joekeen03.biomemapper.util;


import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.ImageLineByte;
import ar.com.hjg.pngj.ImageLineInt;
import ar.com.hjg.pngj.PngWriter;
import com.joekeen03.biomemapper.BiomeMapper;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;

public class MapCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "biomemapper";
    }

    @Override
    public String getCommandUsage(ICommandSender iCommandSender) {
        return "commands.biomemapper.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        // Validate the user can use this command
        if (!sender.canCommandSenderUseCommand(this.getRequiredPermissionLevel(), this.getCommandName()) && !MinecraftServer.getServer().isSinglePlayer()) {
            SendChatMessage(sender, new ChatComponentTranslation("commands.generic.permission", new Object[0]));
        }
        else {
            if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                SendChatMessage(sender, new ChatComponentTranslation("commands.generic.permission", new Object[0]));
            }
            else {
                try {
                    int xStart = Integer.parseInt(args[0]);
                    int zStart = Integer.parseInt(args[1]);
                    int xLength = Integer.parseInt(args[2]);
                    int zLength = Integer.parseInt(args[3]);
                    String imageName = args[4];
                    // Yes, this should be in another thread, but not gonna worry about that for now.
                    // Just trying to get this working.
                    MapBiomes(sender.getEntityWorld(), xStart, zStart, xLength, zLength, imageName);
                }
                catch (NumberFormatException e) {
                    e.printStackTrace();
                    SendChatMessage(sender, new ChatComponentTranslation("commands.numberFormatException"));
                }
                catch (Exception e) {
                    e.printStackTrace();
                    SendChatMessage(sender, new ChatComponentTranslation("commands.failed"));
                }
            }
        }
    }

    public void SendChatMessage(ICommandSender sender, ChatComponentTranslation chatTranslation) {
        MinecraftServer.getServer().addChatMessage(chatTranslation);
        sender.addChatMessage(new ChatComponentText(chatTranslation.getUnformattedTextForChat()));
    }

    /**
     * Creates a biome map of a world for the block coords provided. Area spans startX to startX+xLength, and likewise
     * for the z-coord.
     * @param world
     * @param startX
     * @param startZ
     * @param xLength
     * @param zLength
     * @param imageName
     */
    public static void MapBiomes(World world, int startX, int startZ, int xLength, int zLength, String imageName) {
        BiomeMapper.info("Starting .png generation for region: ("+startX+", "+startZ+") to ("+(startX+xLength)+", "+(startZ+zLength)+").");
        File imageFile = new File(imageName+".png");
        ImageInfo pngInfo = new ImageInfo(xLength, zLength, 8, false);
        PngWriter pngWriter = new PngWriter(imageFile, pngInfo);
        // Stores up to three rows at a time - the
        int[][] IDs = new int[2][xLength];
        int[][] colors = new int[2][xLength];
        byte[] writeColors = new byte[xLength*3];

        FillRow(world, IDs[1], colors[1], startX, xLength, zLength-1);
        int currID;
        // Iterate from +z to -z, because rows in the png are written top-down - and we want the top to be the +z side
        for (int dz = zLength-2; dz >= 0; dz--) {
            // Swap the current and previous color/ID arrays
            SwapArrays(IDs);
            SwapArrays(colors);
            // Grab the next row of biomes
            FillRow(world, IDs[1], colors[1], startX, xLength, startZ+dz);
            // Setting any pixels where the biome changes to the right or down - "boundary" pixels - to black
            // Only check right & down, because it would otherwise set the pixels on both sides of the boundary.
            // Yes, this creates a bias, but it's a consistent one (and given the scale of biomes, I don't think it's an
            // issue).
            for (int dx = 0; dx < xLength-1; dx++) {
                currID = IDs[0][dx];
                if (currID != IDs[0][dx+1] || currID != IDs[1][dx])
                {
                    colors[0][dx] = 0;
                }
            }
            // Special handling for the right-most pixel
            currID = IDs[0][xLength-1];
            if (currID != IDs[1][xLength-1])
            {
                colors[0][xLength-1] = 0;
            }
            WriteColors(colors[0], writeColors, dz-1, pngWriter, pngInfo);
        }
        // Special handling for the last row.
        for (int dx = 0; dx < xLength-1; dx++) {
            currID = IDs[1][dx];
            if (currID != IDs[1][dx+1])
            {
                colors[1][dx] = 0;
            }
        }
        // Skip the right-most pixel
        WriteColors(colors[1], writeColors, zLength-1, pngWriter, pngInfo);
        pngWriter.end();
        BiomeMapper.info("Biome map generation complete.");
    }

    /**
     * Fills the provided ID and color arrays with the corrseponding biome information for the specified row.
     * Mutates the arrays *IN PLACE*
     * @param world
     * @param IDs
     * @param colors
     * @param x
     * @param z
     * @param zLength
     */
    public static void FillRow(World world, int[] IDs, int[] colors, int startX, int xLength, int z) {
        for (int dx = 0; dx < xLength; dx++) {
            BiomeGenBase biome = world.getBiomeGenForCoords(startX+dx, z);
            IDs[dx] = biome.biomeID;
            colors[dx] = biome.color;
        }
    }

    public static void SwapArrays(int[][] arr) {
        int[] temp = arr[0];
        arr[0] = arr[1];
        arr[1] = temp;
    }

    /**
     * Writes the provided colors to the specified png. Uses a supplied byte array for unpacking the rgb components, to
     * avoid unnecessary object creation/destruction (I'm looking at you, Mojank)
     * @param colors
     * @param writeColors
     * @param row
     * @param pngWriter
     * @param pngInfo
     */
    public static void WriteColors(int[] colors, byte[] writeColors, int row, PngWriter pngWriter, ImageInfo pngInfo)
    {
        for (int dx = 0; dx < colors.length; dx++) {
            int adjustedX = dx*3;
            writeColors[adjustedX] = (byte)((colors[dx]>>16)&255); // Red
            writeColors[adjustedX+1] = (byte)((colors[dx]>>8)&255); // Green
            writeColors[adjustedX+2] = (byte)(colors[dx]&255); // Blue
        }
        pngWriter.writeRow(new ImageLineByte(pngInfo, writeColors));
    }
}
