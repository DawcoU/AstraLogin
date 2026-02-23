package pl.dawcou.astralogin;

public class IPSecurity {

    // Metoda sprawdzająca, czy początek adresu IP się zgadza
    public boolean isIPSafe(String savedIP, String currentIP) {
        if (savedIP == null || currentIP == null) return false;
        if (savedIP.equals(currentIP)) return true; // Identyczne IP - wpuszczamy

        String[] savedParts = savedIP.split("\\.");
        String[] currentParts = currentIP.split("\\.");

        // Sprawdzamy czy IP ma poprawny format (4 części)
        if (savedParts.length < 2 || currentParts.length < 2) return false;

        // Porównujemy dwa pierwsze człony (np. 123.45.xxx.xxx)
        return savedParts[0].equals(currentParts[0]) && savedParts[1].equals(currentParts[1]);
    }
}