package at.md.Util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class IOHandler {

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
