import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.lang.*;
import java.util.*;
import javax.swing.*;

public class ImageCompressor {
  static final int WIDTH = 512;
  static final int HEIGHT = 512;
  
  public static void main(String[] args) {
    // Get command line arguments
    if (args.length != 2) {
      System.out.println("Incorrect command line arguments.");
      return;
    }
    String fileName = args[0];
    int n = Integer.parseInt(args[1]);
    int m = n/4096;
    // Read in RGB file
    System.out.println("Retrieving image...");
    BufferedImage image = readImage(fileName);
    System.out.println("Converting image to YCbCr space...");
    int[] ycbcr = convertToYCbCr(image);
    // Extract Y from YCbCr
    int[] y = new int[WIDTH*HEIGHT];
    for (int i = 0; i < WIDTH*HEIGHT; i++) {
      y[i] = ycbcr[i];
    }
    // Extract Cb from YCbCr
    int[] cb = new int[WIDTH*HEIGHT];
    int offset = WIDTH*HEIGHT;
    for(int i = 0; i < WIDTH*HEIGHT; i++){  
      cb[i] = ycbcr[i + offset];
    }
    // Extract Cr from YCbCr
    int[] cr = new int[WIDTH*HEIGHT];
    offset = 2*WIDTH*HEIGHT;
    for(int i = 0; i < WIDTH*HEIGHT; i++){  
      cr[i] = ycbcr[i + offset];
    }
    // Perform progressive analysis and finish program if specified in arguments
    if (n == -1) {
      progressiveAnalysis(y, cb, cr);
      return;
    }
    // BEGIN DCT
    System.out.println("BEGIN DCT");
    // Divide into 8x8 blocks for each channel
    System.out.println("Dividing YCbCr channels into 8x8 blocks...");
    ArrayList<int[][]> yBlocks = getBlocks(y);
    ArrayList<int[][]> cbBlocks = getBlocks(cb);
    ArrayList<int[][]> crBlocks = getBlocks(cr);
    // Perform DCT for each channel
    System.out.println("Running DCT on each block...");
    ArrayList<double[][]> yDCT = encodeDCT(yBlocks);
    ArrayList<double[][]> cbDCT = encodeDCT(cbBlocks);
    ArrayList<double[][]> crDCT = encodeDCT(crBlocks);
    // Perform IDCT for each channel to get original
    System.out.println("Running Inverse DCT on each block...");
    ArrayList<int[][]> yIDCT = decodeDCT(yDCT, m);
    ArrayList<int[][]> cbIDCT = decodeDCT(cbDCT, m);
    ArrayList<int[][]> crIDCT = decodeDCT(crDCT, m);
    // Combine blocks back into single array for each channel
    System.out.println("Combining blocks into decoded YCbCr array...");
    int[] newY = combineBlocks(yIDCT);
    int[] newCb = combineBlocks(cbIDCT);
    int[] newCr = combineBlocks(crIDCT);
    int[] idctYCbCr = new int[3*WIDTH*HEIGHT];
    // Add y chanel to new array
    int index = 0;
    for (int i = 0; i < WIDTH*HEIGHT; i++) {
      idctYCbCr[index] = newY[i];
      index ++;
    }
    // Add cb channel to new array
    for (int i = 0; i < WIDTH*HEIGHT; i++) {
      idctYCbCr[index] = newCb[i];
      index ++;
    }
    // Add cr channel to new array
    for (int i = 0; i < WIDTH*HEIGHT; i++) {
      idctYCbCr[index] = newCr[i];
      index ++;
    }
    // BEGIN DWT
    System.out.println("BEGIN DWT");
    System.out.println("Running DWT on each channel...");
    double[][] yDWT = encodeDWT(y);
    double[][] cbDWT = encodeDWT(cb);
    double[][] crDWT = encodeDWT(cr);
    System.out.println("Running Inverse DWT on each channel...");
    int[] yIDWT = decodeDWT(yDWT, n);
    int[] cbIDWT = decodeDWT(cbDWT, n);
    int[] crIDWT = decodeDWT(crDWT, n);
    // Combine channels back into single array
    System.out.println("Combining channels into decoded YCbCr array...");
    int[] idwtYCbCr = new int[3*WIDTH*HEIGHT];
    // Add y chanel to new array
    index = 0;
    for (int i = 0; i < WIDTH*HEIGHT; i++) {
      idwtYCbCr[index] = yIDWT[i];
      index ++;
    }
    // Add cb channel to new array
    for (int i = 0; i < WIDTH*HEIGHT; i++) {
      idwtYCbCr[index] = cbIDWT[i];
      index ++;
    }
    // Add cr channel to new array
    for (int i = 0; i < WIDTH*HEIGHT; i++) {
      idwtYCbCr[index] = crIDWT[i];
      index ++;
    }
    // END DWT
    // Transform color space back to RGB
    System.out.println("Converting decoded results to RGB space...");
    BufferedImage decodedImageDCT = convertToRGB(idctYCbCr);
    BufferedImage decodedImageDWT = convertToRGB(idwtYCbCr);
    // Display decoded image into a jframe
    System.out.println("Displaying results!");
    JFrame frame = new JFrame();
    JLabel dctLabel = new JLabel(new ImageIcon(decodedImageDCT));
    JLabel dwtLabel = new JLabel(new ImageIcon(decodedImageDWT));
    JPanel panel = new JPanel();
    panel.add("DCT", new JScrollPane(dctLabel));
    panel.add("DWT", new JScrollPane(dwtLabel));
    frame.getContentPane().add(panel);
    frame.pack();
    frame.setVisible(true);
  }

  public static BufferedImage readImage (String fileName) {
    BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    try {
      File file = new File(fileName);
      InputStream is = new FileInputStream(file);
      // Get length of file and create byte array
      long len = file.length();
      byte[] bytes = new byte[(int)len];
      // Read all bytes from image file into byte array
      int offset = 0;
      int numRead = 0;
      while (offset < bytes.length && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
        offset += numRead;
      }
      // Set image contents
      int ind = 0;
      for(int y = 0; y < HEIGHT; y++){
        for(int x = 0; x < WIDTH; x++){
          byte r = bytes[ind];
          byte g = bytes[ind+HEIGHT*WIDTH];
          byte b = bytes[ind+HEIGHT*WIDTH*2]; 
          // set the RGB value for a specific pixel
          int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
          img.setRGB(x,y,pix);
          ind++;
        }
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return img;
  }

  public static int[] convertToYCbCr (BufferedImage image) {
    int index = 0;
    int[] ycbcr = new int[3*WIDTH*HEIGHT];
    for (int j = 0; j < HEIGHT; j++) {
      for (int i = 0; i < WIDTH; i++) {
        Color pixel = new Color(image.getRGB(i, j));
        int y = (int)(0.299*pixel.getRed() + 0.587*pixel.getGreen() + 0.114*pixel.getBlue());
        int cb = (int)(-0.159*pixel.getRed() - 0.332*pixel.getGreen() + 0.050*pixel.getBlue());
        int cr = (int)(0.500*pixel.getRed() - 0.419*pixel.getGreen() - 0.081*pixel.getBlue());
        ycbcr[index] = y;
        ycbcr[index+WIDTH*HEIGHT] = cb;
        ycbcr[index+2*WIDTH*HEIGHT] = cr;
        index ++;
      }
    }
    return ycbcr;
  }

  public static BufferedImage convertToRGB (int[] YCbCr) {
    BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    int index = 0;
    for (int y = 0; y < HEIGHT; y++) {
      for (int x = 0; x < WIDTH; x++) {
        int r = (int)(0.871*YCbCr[index] - 0.233*YCbCr[index+WIDTH*HEIGHT] + 1.405*YCbCr[index+2*WIDTH*HEIGHT]);
        int g = (int)(0.221*YCbCr[index] - 1.752*YCbCr[index+WIDTH*HEIGHT] - 0.689*YCbCr[index+2*WIDTH*HEIGHT]);
        int b = (int)(4.236*YCbCr[index] + 7.626*YCbCr[index+WIDTH*HEIGHT] - 0.108*YCbCr[index+2*WIDTH*HEIGHT]);
        // System.out.println("r:" + r + " g:" + g + " b:" + b);
        r = Math.max(0, Math.min(r, 255));
        g = Math.max(0, Math.min(g, 255));
        b = Math.max(0, Math.min(b, 255));
        Color color = new Color(r, g, b);
        image.setRGB(x, y, color.getRGB());
        index ++;
      }
    }
    return image;
  }

  public static ArrayList<int[][]> getBlocks (int[] array) {
    ArrayList<int[][]> blocks = new ArrayList<int[][]>();
    // get all blocks through the entire height
    int index = 0;
    int xOffset = 0;
    int yOffset = 0;
    while(index < array.length) {
      // create 8x8 block
      int[][] block = new int[8][8];
      for (int j = 0; j < 8; j++) {
        for (int i = 0; i < 8; i++) {
          block[j][i] = array[i + xOffset + (j+yOffset)*WIDTH];
          index ++;
        }
      }
      xOffset += 8;
      if (xOffset == WIDTH) {
        xOffset = 0;
        yOffset += 8;
      }
      blocks.add(block);
    }
    return blocks;
  }

  public static int[] combineBlocks(ArrayList<int[][]> blocks) {
    int[] array = new int[blocks.size()*64];
    int xOffset = 0;
    int yOffset = 0;
    for (int[][] block : blocks) {
      for (int j = 0; j < 8; j++) {
        for (int i = 0; i < 8; i++) {
          array[i + xOffset + (j+yOffset)*WIDTH] = block[j][i];
        }
      }
      xOffset += 8;
      if (xOffset == WIDTH) {
        xOffset = 0;
        yOffset += 8;
      }
    }
    return array;
  }

  public static ArrayList<double[][]> encodeDCT(ArrayList<int[][]> blocks) {
    ArrayList<double[][]> dct = new ArrayList<double[][]>();
    for (int[][] block: blocks) {
      dct.add(forwardDCT(block));
    }
    return dct;
  }

  public static double[][] forwardDCT(int[][] block) {
    double[][] dct = new double[8][8];
    double[][] cos = new double[8][8];
    // prepopulate list of cosine values
    for (int u = 0; u < 8; u++) {
      for (int x = 0; x < 8; x++) {
        cos[u][x] = Math.cos((2*x+1)*u*Math.PI/16);
      }
    }
    // loop for u
    for (int u = 0; u < 8; u++) {
      double cu = 1;
      if (u == 0)
        cu = 1/Math.sqrt(2);
      // loop for v
      for (int v = 0; v < 8; v++) {
        double cv = 1;
        if (v == 0)
          cv = 1/Math.sqrt(2);
        // keep track of sum
        double sum = 0;
        // get sum for all x and y
        for (int x = 0; x < 8; x++) {
          for (int y = 0; y < 8; y++) {
            sum += block[y][x] * cos[u][x] * cos[v][y];
          }
        }
        // get value for F(u, v)
        dct[v][u] = (0.25 * cu * cv * sum);
      }
    }
    return dct;
  }

  public static ArrayList<int[][]> decodeDCT(ArrayList<double[][]> blocks, int m) {
    // use coefficient to select first m values in zig zag
    for (double[][] block : blocks) {
      int count = 0;
      // zig zag through upper left half
      for (int j = 0; j < 8; j++) {
        int x = 0;
        int y = j;
        while(x < 8 && y >= 0) {
          if (count == m) {
            block[y][x] = 0;
          } else {
            count ++;
          }
          x ++;
          y --;
        }
      }
      // zig zag through lower right half
      for (int i = 1; i < 8; i++) {
        int x = i;
        int y = 7;
        while(x < 8 && y >= 0) {
          if (count == m) {
            block[y][x] = 0;
          } else {
            count ++;
          }
          x ++;
          y --;
        }
      }
    }
    // run IDCT on each block
    ArrayList<int[][]> idct = new ArrayList<int[][]>();
    for (double[][] block : blocks) {
      idct.add(inverseDCT(block));
    }
    return idct;
  }

  public static int[][] inverseDCT(double[][] block) {
    int original[][] = new int[8][8];
    double[][] cos = new double[8][8];
    // prepopulate list of cosine values
    for (int u = 0; u < 8; u++) {
      for (int x = 0; x < 8; x++) {
        cos[u][x] = Math.cos((2*x+1)*u*Math.PI/16);
      }
    }
    // loop for x
    for (int x = 0; x < 8; x++) {
      // loop for y
      for (int y = 0; y < 8; y++) {
        // keep track of sum
        double sum = 0;
        // get sum for all u and v
        for (int u = 0; u < 8; u++) {
          double cu = 1;
          if (u == 0) {
            cu = 1/Math.sqrt(2);
          }
          for (int v = 0; v < 8; v++) {
            double cv = 1;
            if (v == 0) {
              cv = 1/Math.sqrt(2);
            }
            sum += cu * cv * block[v][u] * cos[u][x] * cos[v][y];
          }
        }
        // get value for F(u, v)
        original[y][x] = (int)(0.25 * sum);
      }
    }
    return original;
  }

  public static double[][] encodeDWT (int[] input) {
    double[][] data = new double[HEIGHT][WIDTH];
    // convert 1D array into 2D array
    int index = 0;
    for (int y = 0; y < HEIGHT; y++) {
      for (int x = 0; x < WIDTH; x++) {
        data[y][x] = input[index];
        index ++;
      }
    }
    // run DWT over log(512)=9 iterations    
    for (int k = 0; k < 9; k++) {
      int size = WIDTH/(1 << k);
      // DWT every row
      for (int j = 0; j < size; j++) {
        double[] row = new double[size];
        for (int i = 0; i < size; i++) {
          row[i] = data[j][i];
        }
        row = forwardDWT(row);
        for (int i = 0; i < size; i++) {
          data[j][i] = row[i];
        }
      }
      // DWT every column
      for (int i = 0; i < size; i++) {
        double[] column = new double[size];
        for (int j = 0; j < size; j++) {
          column[j] = data[j][i];
        }
        column = forwardDWT(column);
        for (int j = 0; j < size; j++) {
          data[j][i] = column[j];
        }
      }
    }
    return data;
  }

  public static double[] forwardDWT(double[] input) {
    double[] output = new double[input.length];
    int length = input.length/2;
    for (int i = 0; i < length; i++) {
      output[i] = 0.5*(input[2*i] + input[2*i + 1]);
      output[length + i] = 0.5*(input[i*2] - input[i*2 + 1]);
    }
    return output;
  }

  public static int[] decodeDWT(double[][] data, int n) {
    // use first n coefficients in a zig zag
    int count = 4;
    int power = 2;
    boolean LH = true;
    boolean HL = false;
    boolean HH = false;
    while(count < data.length * data.length) {
      int xOffset = 0;
      int yOffset = 0;
      if (LH) {
        xOffset = power;
      } else if (HL) {
        yOffset = power;
      } else if (HH) {
        xOffset = power;
        yOffset = power;
      }
      for (int j = 0; j < power; j++) {
        for (int i = 0; i < power; i++) {
          if (count >= n) {
            data[yOffset + j][xOffset + i] = 0;
          }
          count ++;
        }
      }
      if (LH) {
        LH = false;
        HL = true;
      } else if (HL) {
        HL = false;
        HH = true;
      } else if (HH) {
        HH = false;
        LH = true;
        power = power * 2;
      } else {
        LH = true;
      }
    }
    // run IDWT to get decoded output
    for (int k = 8; k >= 0; k--) {
      int size = WIDTH/(1 << k);
      // inverse DWT each column
      double[] column = new double[size];
      for (int i = 0; i < size; i++) {
        for (int j = 0; j < size; j++) {
          column[j] = data[j][i];
        }
        column = inverseDWT(column);
        for (int j = 0; j < size; j++) {
          data[j][i] = column[j];
        }
      }
      // inverse DWT each row
      double[] row = new double[size];
      for (int j = 0; j < size; j++) {
        for (int i = 0; i < size; i++) {
          row[i] = data[j][i];
        }
        row = inverseDWT(row);
        for (int i = 0; i < size; i++) {
          data[j][i] = row[i];
        }
      }
    }
    // convert 2d input into 1d output array
    int output[] = new int[WIDTH*HEIGHT];
    int index = 0;
    for (int y = 0; y < HEIGHT; y++) {
      for (int x = 0; x < WIDTH; x++) {
        output[index] = (int) data[y][x];
        index ++;
      }
    }
    return output;
  }

  public static double[] inverseDWT(double[] input) {
    double[] output = new double[input.length];
    int length = input.length/2;
    for (int i = 0; i < length; i++) {
      output[2*i] = input[i] + input[i + length];
      output[2*i + 1] = input[i] - input[i + length];
    }
    return output;
  }

  public static void progressiveAnalysis(int[] y, int[] cb, int[] cr) {
    JFrame frame = new JFrame();
    // perform 64 iterations of DCT vs DWT
    System.out.println("Performing progressive analysis...");
    for (int m = 1; m <= 64; m++) {
      System.out.println("Iteration " + m);
      int n = m*4096;
      // get 8x8 blocks for DCT
      final ArrayList<int[][]> yBlocks = getBlocks(y);
      final ArrayList<int[][]> cbBlocks = getBlocks(cb);
      final ArrayList<int[][]> crBlocks = getBlocks(cr);
      // perform DCT for each channel
      final ArrayList<double[][]> yDCT = encodeDCT(yBlocks);
      final ArrayList<double[][]> cbDCT = encodeDCT(cbBlocks);
      final ArrayList<double[][]> crDCT = encodeDCT(crBlocks);
      // perform DWT for each channel
      final double[][] yDWT = encodeDWT(y);
      final double[][] cbDWT = encodeDWT(cb);
      final double[][] crDWT = encodeDWT(cr);
      // inverse DCT
      ArrayList<int[][]> yIDCT = decodeDCT(yDCT, m);
      ArrayList<int[][]> cbIDCT = decodeDCT(cbDCT, m);
      ArrayList<int[][]> crIDCT = decodeDCT(crDCT, m);
      int[] newY = combineBlocks(yIDCT);
      int[] newCb = combineBlocks(cbIDCT);
      int[] newCr = combineBlocks(crIDCT);
      int[] idctYCbCr = new int[3*WIDTH*HEIGHT];
      int index = 0;
      for (int i = 0; i < WIDTH*HEIGHT; i++) {
        idctYCbCr[index] = newY[i];
        index ++;
      }
      for (int i = 0; i < WIDTH*HEIGHT; i++) {
        idctYCbCr[index] = newCb[i];
        index ++;
      }
      for (int i = 0; i < WIDTH*HEIGHT; i++) {
        idctYCbCr[index] = newCr[i];
        index ++;
      }
      // inverse DWT
      int[] yIDWT = decodeDWT(yDWT, n);
      int[] cbIDWT = decodeDWT(cbDWT, n);
      int[] crIDWT = decodeDWT(crDWT, n);
      int[] idwtYCbCr = new int[3*WIDTH*HEIGHT];
      index = 0;
      for (int i = 0; i < WIDTH*HEIGHT; i++) {
        idwtYCbCr[index] = yIDWT[i];
        index ++;
      }
      for (int i = 0; i < WIDTH*HEIGHT; i++) {
        idwtYCbCr[index] = cbIDWT[i];
        index ++;
      }
      for (int i = 0; i < WIDTH*HEIGHT; i++) {
        idwtYCbCr[index] = crIDWT[i];
        index ++;
      }
      // display results side by side
      BufferedImage decodedImageDCT = convertToRGB(idctYCbCr);
      BufferedImage decodedImageDWT = convertToRGB(idwtYCbCr);
      // Display decoded image into a jframe
      JLabel dctLabel = new JLabel(new ImageIcon(decodedImageDCT));
      JLabel dwtLabel = new JLabel(new ImageIcon(decodedImageDWT));
      JPanel panel = new JPanel();
      panel.add("DCT", new JScrollPane(dctLabel));
      panel.add("DWT", new JScrollPane(dwtLabel));
      frame.getContentPane().add(panel);
      frame.pack();
      frame.setVisible(true);
      try {
        Thread.sleep(1200); 
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    System.out.println("Finished!");
  }

}