package com.wkl.sdat2img;

import java.io.*;
import java.util.*;

/**
 * Created by wangkunlin
 * on 2018/4/10.
 */
class DataParser {
    private static final int BLOCK_SIZE = 4096;

    void run(String[] args) throws Exception {
        String currentPath = System.getProperty("user.dir");

        Transfer transfer = parseTransferListFile(new File(currentPath, args[0]));

        switch (transfer.version) {
            case 1:
                print("Android Lollipop 5.0 detected!");
                break;
            case 2:
                print("Android Lollipop 5.1 detected!");
                break;
            case 3:
                print("Android Marshmallow 6.x detected!");
                break;
            case 4:
                print("Android Nougat 7.x / Oreo 8.x detected!");
                break;
            default:
                print("Unknown Android version!");
                break;
        }

        File outputImg = new File(currentPath, args[2]);

        final String datName = args[1];

        RandomAccessFile raf = new RandomAccessFile(outputImg, "rw");

        Queue<File> files = new LinkedList<>();
        for (int i = 0; i < 1000; i++) {
            File file;
            if (i == 0) {
                file = new File(currentPath, datName);
            } else {
                file = new File(currentPath, datName + "." + i);
            }
            if (file.exists()) {
                print("Found " + file.getName());
                files.add(file);
            } else {
                break;
            }
        }

        long totalBlockCount = doCommand1(transfer, files, raf);
        if (totalBlockCount != transfer.newBlocks) {
            print("Failed!!!");
            print("Transfer blocks " + transfer.newBlocks + " write " + totalBlockCount);
            outputImg.deleteOnExit();
        } else {
            print("Done! Output image: " + outputImg.toString());
        }
    }

    private long doCommand1(Transfer transfer, Queue<File> datFiles, RandomAccessFile raf) throws Exception {

        long maxBlockPos = maxPos(transfer.commands);
        File poll = datFiles.poll();
        if (poll == null) return 0;
        RandomAccessFile datFile;
        if (datFiles.size() == 1) {
            datFile = new RandomAccessFile(poll, "r");
        } else {
            print("Merge files begin");
            File tmpDatFile = new File(poll.getParent(), "system.new.dat.tmp");
            tmpDatFile.deleteOnExit();
            datFile = new RandomAccessFile(tmpDatFile, "rw");
            while (poll != null) {
                RandomAccessFile readDat = new RandomAccessFile(poll, "r");
                print("Merge " + poll.getName());
                byte[] bytes = new byte[BLOCK_SIZE];
                int len;
                while ((len = readDat.read(bytes)) != -1) {
                    datFile.write(bytes, 0, len);
                }
                readDat.close();
                poll = datFiles.poll();
            }
            print("Merge files end");
            datFile.seek(0);
        }

        raf.setLength(maxBlockPos * BLOCK_SIZE);

        print("Start converse");

        long totalBlockCount = 0;
        byte[] bytes = new byte[BLOCK_SIZE];
        for (Command command : transfer.commands) {
            if (command.cmd.equals("new")) {
                for (Range range : command.ranges) {
                    long blockCount = range.end - range.begin;
                    print("Copying " + blockCount + " blocks into position " + range.begin + "...");
                    raf.seek(range.begin * BLOCK_SIZE);
                    while (blockCount > 0) {
                        int len = datFile.read(bytes);
                        if (len == -1) {
                            datFile.close();
                            raf.close();
                            print("Remained block count " + blockCount);
                            return totalBlockCount;
                        }
                        raf.write(bytes, 0, len);
                        blockCount--;
                        totalBlockCount++;
                    }
                }
            } else {
                print("Skipping command " + command.cmd + "...");
            }
        }
        print("Remained dat size " + (datFile.length() - datFile.getFilePointer()));
        datFile.close();
        raf.close();
        return totalBlockCount;
    }

    private Transfer parseTransferListFile(File transFile) throws Exception {
        Transfer transfer = new Transfer();
        FileReader fr = new FileReader(transFile);
        BufferedReader br = new BufferedReader(fr);

        String v = br.readLine();
        int version = Integer.parseInt(v);
        transfer.version = version;

        String blocksStr = br.readLine();
        transfer.newBlocks = Integer.parseInt(blocksStr);

        if (version >= 2) {
            br.readLine();
            br.readLine();
        }

        List<Command> commands = new ArrayList<>();

        String read;
        while ((read = br.readLine()) != null) {
            String[] cmdSplit = read.split(" ");
            String cmd = cmdSplit[0];
            switch (cmd) {
                case "erase":
                case "new":
                case "zero":
                    Command command = new Command();
                    command.cmd = cmd;
                    command.ranges = rangeSet(cmdSplit[1]);
                    commands.add(command);
            }
        }
        br.close();
        fr.close();
        transfer.commands = commands;
        return transfer;
    }

    private long maxPos(List<Command> commands) {
        long maxPox = 0;
        for (Command cmd : commands) {
            for (Range range : cmd.ranges) {
                maxPox = Math.max(maxPox, range.end);
            }
        }
        return maxPox;
    }

    private List<Range> rangeSet(String src) {
        List<Range> ranges = new ArrayList<>();
        String[] rangeSplit = src.split(",");
        int len = Integer.parseInt(rangeSplit[0]);
        if (len + 1 != rangeSplit.length) {
            throw new RuntimeException("Error on parsing following data to range set: \n" + src);
        }
        for (int i = 1; i < rangeSplit.length - 1; i += 2) {
            Range range = new Range();
            range.begin = Long.parseLong(rangeSplit[i]);
            range.end = Long.parseLong(rangeSplit[i + 1]);
            ranges.add(range);
        }
        return ranges;
    }

    private static class Transfer {
        int version;
        long newBlocks;
        List<Command> commands;
    }

    private static class Range {
        long begin;
        long end;
    }

    private static class Command {
        String cmd;
        List<Range> ranges;
    }

    private void print(String info) {
        System.out.println(info);
    }
}
