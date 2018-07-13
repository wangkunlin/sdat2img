package com.wkl.sdat2img;

/**
 * Created by wangkunlin
 * on 2018/4/10.
 */
public class Main {

    public static void main(String args[]) {
        DataParser parser = new DataParser();
        try {
            parser.run(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
