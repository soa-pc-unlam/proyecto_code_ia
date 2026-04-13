package com.ashencostha.mqtt;

import java.io.Serializable;

public class Song implements Serializable {
    private String name;
    private int[][] matrix;

    public Song(String name, int[][] matrix) {
        this.name = name;
        this.matrix = matrix;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int[][] getMatrix() {
        return matrix;
    }

    public void setMatrix(int[][] matrix) {
        this.matrix = matrix;
    }

    @Override
    public String toString() {
        return name;
    }
}
