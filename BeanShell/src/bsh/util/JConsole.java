/*****************************************************************************
 *                                                                           *
 *  This file is part of the BeanShell Java Scripting distribution.          *
 *  Documentation and updates may be found at http://www.beanshell.org/      *
 *                                                                           *
 *  BeanShell is distributed under the terms of the LGPL:                    *
 *  GNU Library Public License http://www.gnu.org/copyleft/lgpl.html         *
 *                                                                           *
 *  Patrick Niemeyer (pat@pat.net)                                           *
 *  Author of Exploring Java, O'Reilly & Associates                          *
 *  http://www.pat.net/~pat/                                                 *
 *                                                                           *
 *****************************************************************************/

package	bsh.util;

import java.awt.Component;
import java.awt.Font;
import java.awt.Color;
import java.awt.Insets;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.util.Vector;

import javax.swing.text.*;
import javax.swing.*;

import bsh.ConsoleInterface;
import bsh.Interpreter;
import bsh.NameSpace;
import bsh.ConsoleInterface;


/**
	A JFC/Swing based console for the BeanShell desktop.
	This is a descendant of the old AWTConsole.

	Improvements by: Mark Donszelmann <Mark.Donszelmann@cern.ch>
		including Cut & Paste

  	Improvements by: Daniel Leuck
		including Color and Image support, key press bug workaround
*/
public final class JConsole extends JScrollPane
    implements ConsoleInterface, Runnable, KeyListener,
	       MouseListener, ActionListener, PropertyChangeListener {

    private final static String	CUT = "Cut";
    private final static String	COPY = "Copy";
    private final static String	PASTE =	"Paste";

	private	OutputStream outPipe;
	private	InputStream inPipe;
	private	InputStream in;
	private	PrintStream out;

	public InputStream getIn() { return in; }
	public PrintStream getOut() { return out;	}
	public PrintStream getErr() { return out;	}

    private int	cmdStart = 0;
	private	Vector history = new Vector();
	private	String startedLine;
	private	int histLine = 0;

    private JPopupMenu menu;
    private JTextPane text;
    private DefaultStyledDocument doc;

	// hack to prevent key repeat for some reason?
    private boolean gotUp = true;

	public JConsole(InputStream cin, OutputStream cout) {
		super();
		Font font = new	Font("Monospaced",Font.PLAIN,14);

		// special TextPane which catches for cut and paste, both L&F keys and
		// programmatic	behaviour
		text = new JTextPane(doc=new DefaultStyledDocument()) {
			public void	cut() {
				if (text.getCaretPosition() < cmdStart)	{
					super.copy();
				} else {
					super.cut();
				}
			}

			public void	paste()	{
				forceCaretMoveToEnd();
				super.paste();
			}
		};

		text.setText("");
		text.setFont( font );
		text.setMargin(	new Insets(7,5,7,5) );
		text.addKeyListener(this);
		setViewportView(text);

		// create popup	menu
		menu = new JPopupMenu("JConsole	Menu");
		menu.add(new JMenuItem(CUT)).addActionListener(this);
		menu.add(new JMenuItem(COPY)).addActionListener(this);
		menu.add(new JMenuItem(PASTE)).addActionListener(this);

		text.addMouseListener(this);

		// make	sure popup menu	follows	Look & Feel
		UIManager.addPropertyChangeListener(this);

		outPipe	= cout;
		if ( outPipe ==	null ) {
			outPipe	= new PipedOutputStream();
			try {
				in = new PipedInputStream((PipedOutputStream)outPipe);
			} catch	( IOException e	) {
				print("Console internal	error (1)...", Color.red);
			}
		}

		inPipe = cin;
		if ( inPipe == null ) {
			PipedOutputStream pout = new PipedOutputStream();
			out = new PrintStream( pout );
			try {
				inPipe = new BlockingPipedInputStream(pout);
			} catch ( IOException e ) { print("Console internal error: "+e); }
		}
		// Start the inpipe watcher
		new Thread( this ).start();

		requestFocus();
	}

	public JConsole() {
		this(null, null);
	}

	public void keyPressed(	KeyEvent e ) {
	    type( e );
	    gotUp=false;
	}

	public void keyTyped(KeyEvent e) {
	    type( e );
	}

    public void	keyReleased(KeyEvent e)	{
		gotUp=true;
		type( e	);
    }

    private void type( KeyEvent	e ) {
		switch ( e.getKeyCode()	) {

			case ( KeyEvent.VK_ENTER ):
			    if (e.getID() == KeyEvent.KEY_PRESSED) {
					if (gotUp) {
						enter();
						cmdStart = text.getText().length();
						text.setCaretPosition(cmdStart);
					}
					e.consume();
				}
				text.repaint();
				break;

			case ( KeyEvent.VK_UP ):
			    if (e.getID() == KeyEvent.KEY_PRESSED) {
				    historyUp();
				}
				e.consume();
				break;

			case ( KeyEvent.VK_DOWN	):
			    if (e.getID() == KeyEvent.KEY_PRESSED) {
					historyDown();
				}
				e.consume();
				break;

			case ( KeyEvent.VK_LEFT	):
			case ( KeyEvent.VK_BACK_SPACE ):
			case ( KeyEvent.VK_DELETE ):
				if (text.getCaretPosition() <= cmdStart) {
					e.consume();
				}
				break;

			case ( KeyEvent.VK_RIGHT ):
				forceCaretMoveToStart();
				break;

			case ( KeyEvent.VK_HOME ):
				text.setCaretPosition(cmdStart);
				e.consume();
				break;

			case ( KeyEvent.VK_U ):	// clear line
				if ( (e.getModifiers() & InputEvent.CTRL_MASK) > 0 ) {
					replaceRange( "", cmdStart, text.getText().length());
					histLine = 0;
					e.consume();
				}
				break;

			case ( KeyEvent.VK_ALT ):
			case ( KeyEvent.VK_CAPS_LOCK ):
			case ( KeyEvent.VK_CONTROL ):
			case ( KeyEvent.VK_META ):
			case ( KeyEvent.VK_SHIFT ):
			case ( KeyEvent.VK_PRINTSCREEN ):
			case ( KeyEvent.VK_SCROLL_LOCK ):
			case ( KeyEvent.VK_PAUSE ):
			case ( KeyEvent.VK_INSERT ):
			case ( KeyEvent.VK_F1):
			case ( KeyEvent.VK_F2):
			case ( KeyEvent.VK_F3):
			case ( KeyEvent.VK_F4):
			case ( KeyEvent.VK_F5):
			case ( KeyEvent.VK_F6):
			case ( KeyEvent.VK_F7):
			case ( KeyEvent.VK_F8):
			case ( KeyEvent.VK_F9):
			case ( KeyEvent.VK_F10):
			case ( KeyEvent.VK_F11):
			case ( KeyEvent.VK_F12):
			case ( KeyEvent.VK_ESCAPE ):

			// only	modifier pressed
			break;

			// Control-C
			case ( KeyEvent.VK_C ):
				if (text.getSelectedText() == null) {
				    if (( (e.getModifiers() & InputEvent.CTRL_MASK) > 0	)
					&& (e.getID() == KeyEvent.KEY_PRESSED))	{
						append("^C");
					}
					e.consume();
				}
				break;

			default:
				if ( 
					(e.getModifiers() & 
					(InputEvent.CTRL_MASK 
					| InputEvent.ALT_MASK | InputEvent.META_MASK)) == 0 ) 
				{
					// plain character
					forceCaretMoveToEnd();
				}
				break;
		}
	}

	private	void append(String string) {
		int slen = text.getText().length();
		text.select(slen, slen);
	    text.replaceSelection(string);
    }

    String replaceRange(Object s, int start, int	end) {
		String st = s.toString();
		text.select(start, end);
	    text.replaceSelection(st);
	    //text.repaint();
	    return st;
    }

	private	void forceCaretMoveToEnd() {
		if (text.getCaretPosition() < cmdStart)	{
			// move caret first!
			text.setCaretPosition(text.getText().length());
		}
		text.repaint();
    }

	private	void forceCaretMoveToStart() {
		if (text.getCaretPosition() < cmdStart)	{
			// move caret first!
		}
		text.repaint();
    }


	private	void enter() {
		String s = getCmd();

		if ( s.length()	== 0 )	// special hack	for empty return!
			s = ";\n";
		else {
			history.addElement( s );
			s = s +"\n";
		}

		append("\n");
		histLine = 0;
		acceptLine( s );
		text.repaint();
	}

    private String getCmd() {
		String s = "";
		try {
			s =	text.getText(cmdStart, text.getText().length() - cmdStart);
		} catch	(BadLocationException e) {
			// should not happen
			System.out.println("Internal JConsole Error: "+e);
		}
		return s;
    }

	private	void historyUp() {
		if ( history.size() == 0 )
			return;
		if ( histLine == 0 )  // save current line
			startedLine = getCmd();
		if ( histLine <	history.size() ) {
			histLine++;
			showHistoryLine();
		}
	}
	private	void historyDown() {
		if ( histLine == 0 )
			return;

		histLine--;
		showHistoryLine();
	}

	private	void showHistoryLine() {
		String showline;
		if ( histLine == 0 )
			showline = startedLine;
		else
			showline = (String)history.elementAt( history.size() - histLine	);

		replaceRange( showline,	cmdStart, text.getText().length() );
		text.setCaretPosition(text.getText().length());
		text.repaint();
	}

	private	void acceptLine( String	line ) {
		if (outPipe == null )
			print("Console internal	error: cannot output ...", Color.red);
		else
			try {
				outPipe.write( line.getBytes() );
				outPipe.flush();
			} catch	( IOException e	) {
				outPipe	= null;
				throw new RuntimeException("Console pipe broken...");
			}
		//text.repaint();
	}

	public void println(String string) {
	    print( string + "\n" );
		text.repaint();
	}

	public synchronized void print(String string) {
	    append( (string==null) ? "null" : string );
		cmdStart = text.getText().length();
		text.setCaretPosition(cmdStart);
	}

	/**
	  * Prints "\\n" (i.e. newline)
	  */

	public void println() {
	    print("\n");
		text.repaint();
	}

	public void println(Object object) {
	    // ugly but	fast
		print(new StringBuffer(
		    String.valueOf(object)).append("\n"));
		text.repaint();
	}

	public void println(Icon icon) {
		print(icon);
		println();
		text.repaint();
	}

	/**
	  * Prints all primitive integer values
	  * (i.e. byte,	short, int, and	long)
	  */
	public void println(long l) {
		println(String.valueOf(l));
	}

	/**
	  * Prints the primitive type "double"
	  */
	public void println(double d) {
		println(String.valueOf(d));
	}

	/**
	  * Prints the primitive type "float"
	  * (needed because of float->double
	  * coercion weirdness)
	  */
	public void println(float f) {
		println(String.valueOf(f));
	}

	public void println(boolean b) {
		println((b ? "true" : "false"));
	}

	public void println(char c) {
		println(String.valueOf(c));
	}

	public synchronized void print(Object object) {
	    append(String.valueOf(object));
		cmdStart = text.getText().length();
		text.setCaretPosition(cmdStart);
	}

	public synchronized void print(Icon icon) {
	    if (icon==null) 
			return;

		text.insertIcon(icon);
		cmdStart = text.getText().length();
		text.setCaretPosition(cmdStart);
	}

	/**
	  * Prints all primitive integer values
	  * (i.e. byte,	short, int, and	long)
	  */
	public void print(long l) {
		print(String.valueOf(l));
	}

	/**
	  * Prints the primitive type "double"
	  */
	public void print(double d) {
	print(String.valueOf(d));
	}

	/**
	  * Prints the primitive type "float"
	  * (needed because of float->double
	  * coercion weirdness)
	  */
	public void print(float	f) {
		print(String.valueOf(f));
	}

	public void print(boolean b) {
		print(b	? "true" : "false");
	}

	public void print(char c) {
		print(String.valueOf(c));
	}

	public void print(Object s, Font font) {
		print(s, font, null);
    }

	public void print(Object s, Color color) {
		print(s, null, color);
    }

	public synchronized void print(Object s, Font font, Color color) {
	    AttributeSet old = getStyle();

	    setStyle(font, color);
		print(s);
		setStyle(old, true);
    }

	public synchronized void print(
	    Object s,
	    String fontFamilyName,
	    int	size,
	    Color color
	    ) {
	    AttributeSet old = getStyle();

	    setStyle(fontFamilyName, size, color);
		print(s);
		setStyle(old, true);
    }

	public synchronized void print(
	    Object s,
	    String fontFamilyName,
	    int	size,
	    Color color,
	    boolean bold,
	    boolean italic,
	    boolean underline
	    ) 
	{

	    AttributeSet old = getStyle();

	    setStyle(fontFamilyName, size, color, bold,	italic,	underline);
		print(s);
		setStyle(old, true);
    }

    public AttributeSet	setStyle(Font font) {
	    return setStyle(font, null);
    }

    public AttributeSet	setStyle(Color color) {
	    return setStyle(null, color);
    }

    public AttributeSet	setStyle( Font font, Color color) 
	{
	    if (font!=null)
			return setStyle( font.getFamily(), font.getSize(), color, 
				font.isBold(), font.isItalic(), 
				StyleConstants.isUnderline(getStyle()) );
		else
			return setStyle(null,-1,color);
    }

    public synchronized	AttributeSet setStyle (
	    String fontFamilyName, int	size, Color color) 
	{
		MutableAttributeSet attr = new SimpleAttributeSet();
		if (color!=null)
			StyleConstants.setForeground(attr, color);
		if (fontFamilyName!=null)
			StyleConstants.setFontFamily(attr, fontFamilyName);
		if (size!=-1)
			StyleConstants.setFontSize(attr, size);

		setStyle(attr);

		return getStyle();
    }

    public synchronized	AttributeSet setStyle(
	    String fontFamilyName,
	    int	size,
	    Color color,
	    boolean bold,
	    boolean italic,
	    boolean underline
	    ) 
	{
		MutableAttributeSet attr = new SimpleAttributeSet();
		if (color!=null)
			StyleConstants.setForeground(attr, color);
		if (fontFamilyName!=null)
			StyleConstants.setFontFamily(attr, fontFamilyName);
		if (size!=-1)
			StyleConstants.setFontSize(attr, size);
		StyleConstants.setBold(attr, bold);
		StyleConstants.setItalic(attr, italic);
		StyleConstants.setUnderline(attr, underline);

		setStyle(attr);

		return getStyle();
    }

    public void	setStyle(AttributeSet attributes) {
		setStyle(attributes, false);
    }

    public void	setStyle(AttributeSet attributes, boolean overWrite) {
		text.setCharacterAttributes(attributes,	overWrite);
    }

    public AttributeSet	getStyle() {
		return text.getCharacterAttributes();
    }

	public void setFont( Font font ) {
		super.setFont( font );

		if ( text != null )
			text.setFont( font );
	}

	private	void inPipeWatcher() throws IOException	{
		byte []	ba = new byte [256]; //	arbitrary blocking factor
		int read;
		while (	(read =	inPipe.read(ba)) != -1 ) {
			print( new String(ba, 0, read) );
			//text.repaint();
		}

		println("Console: Input	closed...");
	}

	public void run() {
		try {
			inPipeWatcher();
		} catch	( IOException e	) {
			print("Console: I/O Error: "+e+"\n", Color.red);
		}
	}

	public String toString() {
		return "BeanShell console";
	}

    // MouseListener Interface
    public void	mouseClicked(MouseEvent	event) {
    }

    public void mousePressed(MouseEvent event) {
        if (event.isPopupTrigger()) {
            menu.show(
				(Component)event.getSource(), event.getX(), event.getY());
        }
    }

    public void	mouseReleased(MouseEvent event)	{
		if (event.isPopupTrigger()) {
			menu.show((Component)event.getSource(), event.getX(),
			event.getY());
		}
		text.repaint();
    }

    public void	mouseEntered(MouseEvent	event) { }

    public void	mouseExited(MouseEvent event) { }

    // property	change
    public void	propertyChange(PropertyChangeEvent event) {
		if (event.getPropertyName().equals("lookAndFeel")) {
			SwingUtilities.updateComponentTreeUI(menu);
		}
    }

    // handle cut, copy	and paste
    public void	actionPerformed(ActionEvent event) {
		String cmd = event.getActionCommand();
		if (cmd.equals(CUT)) {
			text.cut();
		} else if (cmd.equals(COPY)) {
			text.copy();
		} else if (cmd.equals(PASTE)) {
			text.paste();
		}
    }


	/**
		The overridden read method in this class will not throw "Broken pipe"
		IOExceptions;  It will simply wait for new writers and data.
		This is used by the JConsole internal read thread to allow writers
		in different (and in particular ephemeral) threads to write to the pipe.

		It also checks a little more frequently than the original read().

		Warning: read() will not even error on a read to an explicitly closed 
		pipe (override closed to for that).
	*/
	public static class BlockingPipedInputStream extends PipedInputStream
	{
		boolean closed;
		public BlockingPipedInputStream( PipedOutputStream pout ) 
			throws IOException 
		{
			super(pout);
		}
		public synchronized int read() throws IOException {
			if ( closed )
				throw new IOException("stream closed");

			while (super.in < 0) {	// While no data */
				notifyAll();	// Notify any writers to wake up
				try {
					wait(750);
				} catch ( InterruptedException e ) {
					throw new InterruptedIOException();
				}
			}
			// This is what the superclass does.
			int ret = buffer[super.out++] & 0xFF;
			if (super.out >= buffer.length)
				super.out = 0;
			if (super.in == super.out)
				super.in = -1;  /* now empty */
			return ret;
		}
		public void close() throws IOException {
			closed = true;
			super.close();
		}
	}

}

