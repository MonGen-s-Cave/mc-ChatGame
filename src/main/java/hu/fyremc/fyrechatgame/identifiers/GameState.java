package hu.fyremc.fyrechatgame.identifiers;

@SuppressWarnings("unchecked")
public enum GameState {
    INACTIVE,
    ACTIVE;

    private volatile Object data;

    public synchronized <T> T data() {
        return (T) data;
    }

    public synchronized <T> void data(T data) {
        this.data = data;
    }
}
