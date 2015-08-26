#!/usr/bin/env python

from xml.etree import ElementTree as ET

class Vector:
	def __init__(self, *args):
		if len(args) == 1:
			self.tuple = tuple(args[0])
		else:
			self.tuple = tuple(args)

	def __add__(self, other):
		if len(other) != len(self):
			raise ValueError("other length != this length")
		return Vector((sum(x) for x in zip(other, self)))

	def __len__(self):
		return len(self.tuple)

	def __iter__(self):
		return iter(self.tuple)

	def __repr__(self):
		return repr(self.tuple)

	def __str__(self):
		return str(self.tuple)

	def strNoBrackets(self):
		return ','.join(map(str, self.tuple))

	def copy(self):
		return Vector(tuple(self.tuple))


def getPaths(fileName):
	tree = ET.parse(fileName)
	root = tree.getroot()
	inkscapeNamespace = {
		'inkscape':"http://www.inkscape.org/namespaces/inkscape",
		'svg': "http://www.w3.org/2000/svg"
	}

	pathLayer = root.find("svg:g[@inkscape:label='Trace']", inkscapeNamespace)
	docHeight = float(root.get("height"));
	rawPaths = pathLayer.findall("svg:path", inkscapeNamespace)
	paths = []

	for path in rawPaths:
		nodesSplit = path.get("d").split(" ")
		if nodesSplit[0] != "m":
			raise Exception("path %s did not start with an m" % path.get("id"))
		if nodesSplit[-1] != "z":
			raise Exception("path %s was not closed properly" % path.get("id"))
		nodesSplit = nodesSplit[1:-1]
		currentAbsolutePosition = Vector(0,0)
		nodes= []
		for nodeString in nodesSplit:
			node = tuple(map(float, nodeString.split(',')))
			currentAbsolutePosition += node
			node = currentAbsolutePosition.copy()
			node.tuple = (node.tuple[0], docHeight - node.tuple[1]);
			nodes.append(node)

		paths.append(nodes)

	return paths

def outputPaths(paths):
	return '\n'.join((';'.join((node.strNoBrackets() for node in path)) for path in paths))

print(outputPaths(getPaths('campusCentreTrace.svg')))
	
#load svg
#find all paths inside relevant group
#for each path
#get d, confirm is of the form "m blah blah z"
#if so, add to paths
#if not, crack shits
