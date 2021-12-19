package primary;

import foo.Second;

/**
  * first stabs at a homemade Java program
  */
public class Main {
  public static void main(String[] args) {
      System.out.println("2 + 2 is " + add(2,2)); 
  }

  /**
    * One of the simplest methods imaginable
    */
  public static int add(int a, int b) {
    return a + b;
  }

  public static int bar() {
    return Second.subtract(3,1);
  }

}
