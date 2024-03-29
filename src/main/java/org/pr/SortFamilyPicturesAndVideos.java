package org.pr;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.annotations.NotNull;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SortFamilyPicturesAndVideos {

    public static final SimpleDateFormat yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
    public static final SimpleDateFormat yyyyMinusMMMinusdd = new SimpleDateFormat("yyyy-MM-dd-HHmmss");
    public static final SimpleDateFormat yyyyUnderMMUnderdd = new SimpleDateFormat("yyyy_MM_dd_HHmmss");
    public static final SimpleDateFormat yyyyMMdd_HHmmss = new SimpleDateFormat("yyyyMMdd_HHmmss");
    int count = 0;

    private String rootDirIn;
    private String rootDirOut;
    private String nameOfPersonThatTookThePhoto;

    BufferedReader consoleInBufferedReader;

    private final String[] allowedFilmTypes = {"mov", "avi", "mp4"};
    private final String[] allowedPhotoTypes = {"jp(e)?g", "png"};
    private final String[] allowedOtherTypes = {};
    private final String allowedRegex = "((" + String.join(")|(", allowedFilmTypes) + ")|(" + String.join(")|(", allowedPhotoTypes) + "))";

    private String whatsAppFileRegex = "((IMG)|(VID))-([0-9])*-WA(.)*";
    private String signalFileRegex = "signal-[0-9]{4}-[0-9]{2}-[0-9]{2}.*";
    private String androidFileRegex = "((IMG)|(VID))_(\\d{8})_(\\d{6})\\.(.)*";
    private String stupidRegex = ".*?([0-9]{8})_.*";
    private String stupidRegex3 = ".*?([0-9]{8}_[0-9]{6}).*";
    private String stupidRegex4 = ".*?([0-9]{4}_[0-9]{2}_[0-9]{2}_[0-9]{6}).*";

    private final String pathSep = "/";
    private final String typeSep = ".";

    private final SimpleDateFormat dateNamingPattern = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_");
    private final SimpleDateFormat directoryNamingPattern = new SimpleDateFormat("yyyy/MM-MMMM/");

    public static void main(String[] args) {
        new SortFamilyPicturesAndVideos();
    }

    private static class CopyConfig {
        private final String inDirPath;
        private final String inFileName;
        private final String outDirPath;
        private final String outFileName;

        public CopyConfig(String f, String fn, String t, String tn) {
            this.inDirPath = f;
            this.inFileName = fn;
            this.outDirPath = t;
            this.outFileName = tn;
        }
    }

    private void print(String s) {
        System.out.print(s);
    }

    private void println(String s) {
        print(s + "\n");
    }

    private interface Function<R> {
        R apply();
    }

    private void loopUntilOk(Function<Boolean> function) {
        for (; ; ) if (function.apply()) break;
    }

    private synchronized void createDir(File file) throws IOException {
        Files.createDirectories(file.toPath());
    }

    private SortFamilyPicturesAndVideos() {
        consoleInBufferedReader = new BufferedReader(new InputStreamReader(System.in));
        loopUntilOk(this::requestUserInput);
        ArrayList<CopyConfig> filestocopy = getCopyConfigurations();
        if (!askUserIfOkToCopy(filestocopy.size())) return;
        filestocopy.forEach(copyConfiguration -> {
            File outFile = new File(copyConfiguration.outDirPath + copyConfiguration.outFileName);
            try {
                File outDir = new File(copyConfiguration.outDirPath);
                if (!outDir.exists()) createDir(outDir);
                int existenceCount = 0;
                while (fileExists(outFile)) {
                    String fileEnding = "";
                    String pathOld = outFile.getAbsolutePath().replaceAll("\\([0-9]*\\)\\.", typeSep);
                    while (true) {
                        int x = pathOld.length() - 1;
                        fileEnding = pathOld.charAt(x) + fileEnding;
                        pathOld = pathOld.substring(0, x);
                        if (pathOld.charAt(x - 1) == '.') {
                            pathOld = pathOld.substring(0, x - 1);
                            break;
                        }
                    }
                    outFile = new File(pathOld + "(" + (++existenceCount) + ")." + fileEnding);
                }
                createAndCopy(copyConfiguration, outFile);
                println("copy OK: " + copyConfiguration.inFileName + " " + copyConfiguration.inDirPath + " ===> " + outFile.getAbsolutePath());
            } catch (IOException e) {
                println("copy ERROR " + copyConfiguration.inFileName + " " + copyConfiguration.inDirPath + " =X=> " + outFile.getAbsolutePath() + ": " + e.getMessage());
            }
        });
    }

    private synchronized void createAndCopy(CopyConfig ft, File outFile) throws IOException {
        Files.createFile(outFile.toPath());
        Files.copy(new File(ft.inDirPath + ft.inFileName).toPath(), new FileOutputStream(outFile));
    }

    private synchronized boolean fileExists(File outFile) {
        return outFile.exists();
    }

    private boolean askUserIfOkToCopy(int amountOfOperations) {
        try {
            print(amountOfOperations + " files found to copy. Continue? [Y/n] ");
            if (consoleInBufferedReader.readLine().toLowerCase().contains("n")) return false;
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    private ArrayList<CopyConfig> getCopyConfigurations() {
        ArrayList<CopyConfig> filesToCopy = new ArrayList<>();
        File inDir = new File(rootDirIn);
        File outDir = new File(rootDirOut);
        Deque<File> dirDeque = new ArrayDeque<>();
        dirDeque.add(inDir);
        File curDir;

        while ((dirDeque.peek()) != null) {
            curDir = dirDeque.pop();
            File[] files = curDir.listFiles();
            if (files != null) for (File f : files) {
                if (f.isDirectory()) dirDeque.add(f);
                else {
                    if (isAllowed(f)) {
                        Date recordingDate = getRecordingDate(f);
                        String pathIn = curDir.getAbsolutePath() + pathSep;
                        String nameIn = f.getName();
                        String pathOut = "";
                        String nameOut = "";
                        if (recordingDate != null) { //found recording date
                            pathOut = outDir + pathSep + directoryNamingPattern.format(recordingDate);
                            nameOut = dateNamingPattern.format(recordingDate) + nameOfPersonThatTookThePhoto + typeSep + f.getName().replaceAll("(.)*\\.", "");
                        } else { //did not find recording date
                            pathOut = outDir + "/unsorted/";
                            nameOut = nameIn;
                        }
                        filesToCopy.add(new CopyConfig(pathIn, nameIn, pathOut, nameOut));
                    }
                }
            }
        }
        return filesToCopy;
    }

    @NotNull
    private Boolean requestUserInput() {
        try {
            print("Input root dir: ");
            rootDirIn = consoleInBufferedReader.readLine();
            print("Output root dir: ");
            rootDirOut = consoleInBufferedReader.readLine();
            print("Who took the fotos? ");
            nameOfPersonThatTookThePhoto = consoleInBufferedReader.readLine();
        } catch (IOException e) {
            return false;
        }
        return rootDirIn != null && Files.exists(Paths.get(rootDirIn)) &&
                rootDirOut != null && Files.exists(Paths.get(rootDirOut)) &&
                nameOfPersonThatTookThePhoto != null && nameOfPersonThatTookThePhoto.replaceAll("\\s", "").length() > 0;
    }


    private boolean isAllowed(File f) {
        return isMedia(f) || isOtherAllowed(f);
    }

    private boolean isMedia(File f) {
        return isVideo(f) || isPhoto(f);
    }

    private boolean isPhoto(File f) {
        String name = f.getName().toLowerCase();
        int l = name.length();
        for (String type : allowedPhotoTypes) {
            name = name.replaceAll(typeSep + type, "");
        }
        return name.length() != l;
    }

    private boolean isVideo(File f) {
        String name = f.getName().toLowerCase();
        int l = name.length();
        for (String type : allowedFilmTypes) {
            name = name.replaceAll(typeSep + type, "");
        }
        return name.length() != l;
    }

    private boolean isOtherAllowed(File f) {
        String name = f.getName().toLowerCase();
        int l = name.length();
        for (String type : allowedOtherTypes) {
            name = name.replaceAll(typeSep + type, "");
        }
        return name.length() != l;
    }

    private Date getRecordingDate(File f) {
        count++;
        Date d;
        print(count + ": " + f.getAbsolutePath() + " (");
        try {
            if (isVideo(f)) {
                print("film, capture date");
                d = getDateVideo(f);
            } else if (isPhoto(f)) {
                print("photo, capture date");
                d = getDateImage(f);
            } else {
                print("other, creation date");
                BasicFileAttributes view = null;
                try {
                    view = Files.getFileAttributeView(f.toPath(), BasicFileAttributeView.class).readAttributes();
                    FileTime fileTimeCreation = view.creationTime();
                    d = new Date(fileTimeCreation.toMillis());
                } catch (Exception e) {
                    d = null;
                }
            }
            if (d == null) {
                if (f.getName().matches(whatsAppFileRegex)) {
                    print(" whattsapp date");
                    d = yyyyMMdd.parse(f.getName()
                            .replaceAll("((IMG)|(VID))-", "")
                            .replaceAll("-WA.*", ""));
                } else if (f.getName().matches(signalFileRegex)) {
                    print(" signal date");
                    d = yyyyMinusMMMinusdd.parse(f.getName()
                            .replace("signal-", "")
                            .substring(0, 17));
                } else if (f.getName().matches(androidFileRegex)) {
                    print(" android date");
                    d = yyyyMMdd_HHmmss.parse(f.getName()
                            .replaceAll("((IMG)|(VID))_", "")
                            .replaceAll("\\." + allowedRegex, ""));
                } else if (f.getName().matches(stupidRegex3)) {
                    print(" some date with time");
                    String date = getDate(stupidRegex3, f);
                    d = yyyyMMdd_HHmmss.parse(date);
                }  else if (f.getName().matches(stupidRegex4)) {
                    print(" some date with time");
                    String date = getDate(stupidRegex4, f);
                    d = yyyyUnderMMUnderdd.parse(date);
                } else if (f.getName().matches(stupidRegex)) {
                    print(" some date w/o time");
                    String date = getDate(stupidRegex, f);
                    d = yyyyMMdd.parse(date);
                } else {
                    throw new IllegalArgumentException();
                }
            }
        } catch (Exception e) {
            println(" <no date found>)");
            return null;
        }
        println(") -> " + new SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(d));
        return d;
    }

    private String getDate(String stupidRegex, File f) {
        Pattern pattern = Pattern.compile(stupidRegex);
        Matcher matcher = pattern.matcher(f.getName());
        matcher.find();
        String date = matcher.group(1);
        return date;
    }

    public Date getDateImage(File image) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
            String mindate = "9999:99:99 99:99:99";
            Date ret = null;
            Metadata metadata = ImageMetadataReader.readMetadata(image);
            for (Directory directory : metadata.getDirectories()) {
                for (Tag tag : directory.getTags()) {
                    if (tag.getTagName().toLowerCase().contains("date") && (mindate.compareTo(tag.getDescription()) > 0)) {
                        mindate = tag.getDescription();
                        ret = sdf.parse(mindate);
                    }
                }
            }
            return ret;
        } catch (Exception e) {
            return null;
        }
    }

    public static Date getDateVideo(File f) {
        try {
            Date ret = null;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String search = "creation_time";

            String line;
            String[] arguments = new String[]{"/usr/bin/ffprobe", "-show_format", f.getAbsolutePath()};
            Process p = new ProcessBuilder(arguments).start();
            BufferedReader input =
                    new BufferedReader
                            (new InputStreamReader(p.getInputStream()));
            while ((line = input.readLine()) != null) {
                if (line.toLowerCase().contains(search.toLowerCase())) {
                    String dateString = "";
                    int index = line.length() - 1;
                    for (int i = index; i >= 0; i--) {
                        if (line.toCharArray()[i] == '=') break;
                        if (line.toCharArray()[i] == 'T')
                            dateString = ' ' + dateString;
                        else
                            dateString = line.toCharArray()[i] + dateString;
                        if (line.toCharArray()[i] == '.') {
                            dateString = "";
                        }
                    }
                    ret = sdf.parse(dateString);
                    break;
                }
            }
            p.waitFor();
            input.close();
            return ret;
        } catch (Exception e) {
            return null;
        }
    }


}
