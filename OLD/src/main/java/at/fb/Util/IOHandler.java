package at.fb.Util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Helper-class for IO-stuff
 *
 */
public class IOHandler {

    /**
     * Reads the file the path is given to and returns a list with it
     *
     * @param fileName the path to the file
     * @return List with all the lines in the file
     * @throws IOException if something goes wrong while reading the file
     */
    public static ArrayList<String> readFile(String fileName) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(fileName));
        ArrayList<String> lines = new ArrayList<>();
        String newLine;
        while ((newLine = br.readLine()) != null) {
            lines.add(newLine.trim());
        }
        br.close();
        return lines;
    }
}
