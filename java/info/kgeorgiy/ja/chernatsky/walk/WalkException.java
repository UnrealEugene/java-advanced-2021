package info.kgeorgiy.ja.chernatsky.walk;

public class WalkException extends Exception {
    public WalkException(RecursiveWalk.RecursiveWalkError error, Throwable cause) {
        super(String.join(": ", error.getMessage(), cause.getMessage()), cause);
    }

    public WalkException(Walk.RecursiveWalkError error, Throwable cause) {
        super(String.join(": ", error.getMessage(), cause.getMessage()), cause);
    }
}
