package it.flaviosimonelli.isw2;

import it.flaviosimonelli.isw2.exception.ConfigException;
import it.flaviosimonelli.isw2.ui.ConsoleMenu;
import it.flaviosimonelli.isw2.util.Config;
import it.flaviosimonelli.isw2.util.ConfigLoader;

public class Main {



    public static void main(String[] args) {
        ConsoleMenu menu = new ConsoleMenu();
        menu.start();
    }
}