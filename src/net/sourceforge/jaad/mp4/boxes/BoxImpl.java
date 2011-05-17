/*
 * Copyright (C) 2010 in-somnia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.sourceforge.jaad.mp4.boxes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.sourceforge.jaad.mp4.MP4InputStream;

public class BoxImpl implements Box {

	private final String name;
	protected long size, type, left, offset;
	protected Box parent;
	protected final List<Box> children;

	public BoxImpl(String name) {
		this.name = name;

		children = new ArrayList<Box>(4);
	}

	public void setParams(Box parent, long size, long type, long offset, long left) {
		this.size = size;
		this.type = type;
		this.parent = parent;
		this.left = left;
	}

	long getLeft() {
		return left;
	}

	/**
	 * Decodes the given input stream by reading this box and all of its
	 * children (if any).
	 * 
	 * @param in an input stream
	 * @throws IOException if an error occurs
	 */
	public void decode(MP4InputStream in) throws IOException {
		readChildren(in);
	}

	public long getType() {
		return type;
	}

	public long getSize() {
		return size;
	}

	public long getOffset() {
		return offset;
	}

	public Box getParent() {
		return parent;
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return name+" ["+BoxFactory.typeToString(type)+"]";
	}

	//container methods
	public boolean hasChildren() {
		return children.size()>0;
	}

	public boolean hasChild(long type) {
		boolean b = false;
		for(Box box : children) {
			if(box.getType()==type) {
				b = true;
				break;
			}
		}
		return b;
	}

	public Box getChild(long type) {
		Box box = null, b = null;
		int i = 0;
		while(box==null&&i<children.size()) {
			b = children.get(i);
			if(b.getType()==type) box = b;
			i++;
		}
		return box;
	}

	public List<Box> getChildren() {
		return Collections.unmodifiableList(children);
	}

	public List<Box> getChildren(long type) {
		List<Box> l = new ArrayList<Box>();
		for(Box box : children) {
			if(box.getType()==type) l.add(box);
		}
		return l;
	}

	protected void readChildren(MP4InputStream in) throws IOException {
		Box box;
		while(left>0) {
			box = BoxFactory.parseBox(this, in);
			left -= box.getSize();
			children.add(box);
		}
	}

	protected void readChildren(MP4InputStream in, int len) throws IOException {
		Box box;
		for(int i = 0; i<len; i++) {
			box = BoxFactory.parseBox(this, in);
			left -= box.getSize();
			children.add(box);
		}
	}
}
