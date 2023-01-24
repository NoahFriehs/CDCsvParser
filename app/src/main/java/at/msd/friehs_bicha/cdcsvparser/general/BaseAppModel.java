package at.msd.friehs_bicha.cdcsvparser.general;

import java.io.Serializable;
import java.util.ArrayList;

public class BaseAppModel implements Serializable {

    public BaseApp txApp;
    public AppType appType;

    public boolean isRunning;

    /**
     * Creates a new AppModel
     *
     * @param file the file to parse
     * @param appType which app to use
     */
    public BaseAppModel(ArrayList<String> file, AppType appType) {
        this.appType = appType;
    }

}
