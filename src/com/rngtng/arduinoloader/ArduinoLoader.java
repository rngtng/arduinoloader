/*
  you can put a one sentence description of your library here.

  (c) copyright

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation; either
  version 2.1 of the License, or (at your option) any later version.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
 */

package com.rngtng.arduinoloader;

import processing.core.PApplet;

/* java based uploader for arduino and any other %100 open source projects,
 might also work with stk500 by chance  */

//uses rxtx http://users.frii.com/jarvi/rxtx/download.html

//courtesy dcb AT opengauge.org

import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;

public class ArduinoLoader {

	public static final long rxtimeoutms = 5000;

	public static final byte[] hello = { 0x30, 0x20 };

	public static final byte[] okrsp = { 0x14, 0x10 };

	static InputStream input2;
	static OutputStream output2;

	public static int waitForChar(InputStream i) throws Exception {
		long t = System.currentTimeMillis();
		while (i.available() == 0) {
			try {
				Thread.sleep(50);
			} catch (Exception e) {
			}
			if (System.currentTimeMillis() - t > rxtimeoutms) {
				throw new Exception("Timed out waiting for response");
			}
		}
		return i.read();
	}

	public static void chkok(InputStream i) throws Exception {
		if ((waitForChar(i) != okrsp[0]) || (waitForChar(i) != okrsp[1]))
			throw new Exception("Invalid response");
	}

	static int[] image = new int[200000];

	static int imagesize = 0;

	public static int upload(String filename, String comport, int baud, int pageSize) throws Exception {
		return upload(openFile(filename), comport, baud, pageSize);
	}

	public static int upload(BufferedReader in, String comport, int baud, int pageSize) throws Exception {
		// open Port
		PApplet.println("Serial Proxy Starting, port:" + comport + ", baud:" + baud + ", pagesize:" + pageSize);
	//	 + ", file:" + filename);
		
		CommPortIdentifier portId2 = CommPortIdentifier.getPortIdentifier(comport);
		SerialPort port2 = (SerialPort) portId2.open("serial madness2", 4001);

		port2.setSerialPortParams(baud, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
		port2.setDTR(false);
		
		input2 = port2.getInputStream();
		output2 = port2.getOutputStream();
		
		// load the hex file into memory
		parsehex(in);
		
		output2.write(hello);

		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
		}

		while (input2.available() > 0) {
			input2.read();
		}

		for (int x = 0; x < 3; x++) {
			try {
				output2.write(hello);
				chkok(input2);
			} catch (Exception e) {
			}
			try {
				Thread.sleep(500);
			} catch (Exception e) {
			}
		}
		output2.write(hello);
		chkok(input2);

		// write the hex file
		int addr = 0;
		while (addr < imagesize) {
			output2.write('U');
			output2.write((addr / 2) % 256);
			output2.write((addr / 2) / 256);
			output2.write(' ');
			chkok(input2);

			int ps = Math.min(pageSize, imagesize - addr);

			output2.write('d');
			output2.write(ps / 256);
			output2.write(ps % 256);
			output2.write('F');
			for (int x = 0; x < ps; x++) {
				output2.write(image[addr + x]);
			}
			output2.write(' ');
			chkok(input2);
			addr += ps;
		}

		// validate the image
		addr = 0;
		output2.write('U');
		output2.write((addr / 2) % 256);
		output2.write((addr / 2) / 256);
		output2.write(' ');
		chkok(input2);

		output2.write('t');
		output2.write(imagesize / 256);
		output2.write(imagesize % 256);
		output2.write('F');
		output2.write(' ');

		if ((waitForChar(input2) != okrsp[0])) throw new Exception("Invalid response");

		while (addr < imagesize) {
			int c = waitForChar(input2);
			if (c != image[addr])
				throw new Exception("Validation error at offset " + addr
						+ ".  Expected " + image[addr] + " received " + c);
			addr++;
			// System.out.print(hexval(c));
			if (addr % 16 == 0)
				System.out.println("");
		}

		if ((waitForChar(input2) != okrsp[1])) throw new Exception("Invalid response");

		output2.write('Q');
		output2.write(' ');
		chkok(input2);

		port2.setDTR(true);
		port2.close();
		
		return imagesize;
	}

	public static void waitfor(int w) throws Exception {
		int c;
		do {
			while (input2.available() == 0);
			c = input2.read();
			System.out.println((char) c + " " + (int) c);
		} while (c != w);
	}

	static String hexvals[] = { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F" };

	static String hexval(int i) {
		return hexvals[i / 16] + hexvals[i % 16];
	}
	
	static void parsehex(BufferedReader in) throws Exception {

		String t = in.readLine();
		int line = 0;
		imagesize = 0;
		while (t != null) {
			line++;
			if (!":".equals(t.substring(0, 1))) throw new Exception("line#" + line + " Invalid format in hex file ");

			int len = Integer.parseInt(t.substring(1, 3), 16);
			imagesize += len;
			int addr = Integer.parseInt(t.substring(3, 7), 16);
			int type = Integer.parseInt(t.substring(7, 9), 16);
			String datav = t.substring(9, 9 + (len * 2));
			int[] data = new int[datav.length() / 2];
			for (int x = 0; x < data.length; x++) {
				data[x] = Integer.parseInt(datav.substring(x * 2, x * 2 + 2), 16);
			}
			int cksum = Integer.parseInt(t.substring(9 + (len * 2), 11 + (len * 2)), 16);

			// compute checksum of line just read
			int cks = (256 - len) + (256 - (addr / 256)) + (256 - (addr % 256)) + (256 - type);
			for (int x = 0; x < data.length; x++) {
				cks += (256 - data[x]);
			}
			cks %= 256;
			if (cks != cksum)
				throw new Exception("line#" + line + " Invalid checksum in hex file ");

			// copy to the image so we can work with the page size easier
			for (int x = 0; x < data.length; x++) {
				image[addr + x] = data[x];
			}

			t = in.readLine();
		}

	}

	private static BufferedReader openFile(String fname) throws Exception {
		PApplet.println("Open Hex File: " + fname);
		return new BufferedReader(new FileReader(fname));	
	}

}