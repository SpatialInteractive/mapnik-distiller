package net.rcode.mapnikdistiller;

public class Utils {
	public static String normalizeSqlDigest(String sqlin) {
		StringBuilder sb=new StringBuilder(sqlin.length());
		char c, prevc=0;
		int state=0;	// 0=grammar, 1=single quote, 2=double quote
		for (int i=0; i<sqlin.length(); i++) {
			c=sqlin.charAt(i);
			
			switch (state) {
			case 0:
				// Normalize all control chars to a single space
				if (c<=32) {
					if (prevc==0) c=0;
					else c=32;
				} else {
					if (prevc==32) {
						sb.append(' ');
					}
					if (c=='\'') {
						state=1;
						if (prevc!='\'') sb.append('\'');
					} else if (c=='"') {
						state=2;
						if (prevc!='"') sb.append('"');
					} else {
						sb.append(c);
					}
				}
				break;
				
			case 1: // single quote
				if (c=='\'') {
					state=0;
				}
				sb.append(c);
				break;
				
			case 2: // double quote
				if (c=='"') {
					state=0;
				}
				sb.append(c);
				break;
			}
			
			prevc=c;
		}
		
		return sb.toString();
	}

	public static String legalizeSqlName(String name) {
		if (name==null || name.length()==0) return "__naname__";
		
		StringBuilder ret=new StringBuilder(name.length()*2);
		char c;
		c=Character.toLowerCase(name.charAt(0));
		if (c<'a' || c>'z') {
			// Start with underscore
			ret.append('_');
		}
		
		for (int i=0; i<name.length(); i++) {
			c=Character.toLowerCase(name.charAt(i));
			if ((c>='0' && c<='9') || (c>='a' && c<='z') || c=='_') {
				ret.append(c);
			} else {
				if (ret.length()>0 && ret.charAt(ret.length()-1)!='_') {
					ret.append('_');
				}
			}
		}
		
		return ret.toString();
	}
}
