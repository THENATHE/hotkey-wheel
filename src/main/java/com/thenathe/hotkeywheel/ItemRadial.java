package com.thenathe.hotkeywheel;

/** A radial of items opened from a wheel: the Tool Pouch item radial and the container chooser. */
public interface ItemRadial {
    int centerX();

    int centerY();

    void cyclePage(int direction);

    /** Uses the hovered item, if any, and closes. */
    void confirm();

    void onClose();
}
