package org.matsim.playground.testingScript;

import java.util.Random;

public class HelloWorld {
    public static void main(String[] args) {
        int x = 0;
        int xMax = 10;
        while (x <= xMax) {
            System.out.println("x = " + x);
            if (x == 5) {
                xMax = 7;
            }
            x++;
        }

        Random random1 = new Random(1);
        Random random2 = new Random(1L);

        System.out.println("Random 1: " + random1.nextInt(100) + " " + random1.nextInt(100) + " " + random1.nextInt(100) + " " + random1.nextInt(100));
        System.out.println("Random 2: " + random2.nextInt(100) + " " + random2.nextInt(100) + " " + random2.nextInt(100) + " " + random2.nextInt(100));


    }

}
