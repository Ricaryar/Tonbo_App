package com.example.tonbo_app;

import android.os.Parcel;
import android.os.Parcelable;

public class HomeFunction implements Parcelable {
    private String id; // 功能ID，用於識別功能
    private String name;
    private String description;
    private int iconResId;

    public HomeFunction(String id, String name, String description, int iconResId) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.iconResId = iconResId;
    }

    protected HomeFunction(Parcel in) {
        id = in.readString();
        name = in.readString();
        description = in.readString();
        iconResId = in.readInt();
    }

    public static final Creator<HomeFunction> CREATOR = new Creator<HomeFunction>() {
        @Override
        public HomeFunction createFromParcel(Parcel in) {
            return new HomeFunction(in);
        }

        @Override
        public HomeFunction[] newArray(int size) {
            return new HomeFunction[size];
        }
    };

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getIconResId() {
        return iconResId;
    }

    public void setIconResId(int iconResId) {
        this.iconResId = iconResId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeString(description);
        dest.writeInt(iconResId);
    }
}
