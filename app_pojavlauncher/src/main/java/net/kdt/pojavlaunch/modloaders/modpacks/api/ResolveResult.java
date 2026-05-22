package net.kdt.pojavlaunch.modloaders.modpacks.api;

import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import java.util.List;

public class ResolveResult {
    public boolean success;
    public String errorMessage;

    public List<ModItem> modsToInstall;
    public List<ModItem> dependenciesToInstall;
    public List<ModItem> alreadyInstalled;

    public ResolveResult() {
        modsToInstall = new java.util.ArrayList<>();
        dependenciesToInstall = new java.util.ArrayList<>();
        alreadyInstalled = new java.util.ArrayList<>();
    }
}
