#!/usr/bin/env python3

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

    @property
    def x(self):
        return self.tuple[0]

    @x.setter
    def x(self, newValue):
        self.tuple = (newValue,)+self.tuple[1:]

    @property
    def y(self):
        return self.tuple[1]

    @y.setter
    def y(self, newValue):
        self.tuple = (self.tuple[0], newValue) + self.tuple[2:]

    #hacky, for now only works when self is a 2vec and other is a numpy 3matrix
    def __mul__(self, other):
        result = (self.tuple + (1, ))*other
        return Vector(result.tolist()[0])




fileName = 'campusCentreTrace.svg'

tree = ET.parse(fileName)
root = tree.getroot()
inkscapeNamespace = {
    'inkscape': "http://www.inkscape.org/namespaces/inkscape",
    'svg': "http://www.w3.org/2000/svg"
}

docHeight = float(root.get("height"))


def calculateTransform():
    import numpy as np
    from scipy import linalg as spl

    refLayer = root.find("svg:g[@inkscape:label='CoordRef']", inkscapeNamespace)
    rawRefs = refLayer.findall("svg:circle", inkscapeNamespace)
    localCoords = []
    latLongCoords = []

    # refs are a bunch of points that I've manually notated with a latitude
    # and longitude. We can use these points to build a transform from svg
    # coords to lat-long coords.
    for ref in rawRefs:
        x = float(ref.get("cx"))
        y = float(ref.get("cy"))
        lat = float(ref.get("x-lat"))
        lon = float(ref.get("x-long"))

        localCoords.append([x, y])
        latLongCoords.append([lat, lon])

    pointsLocal = np.matrix(localCoords)
    pointsLatLong = np.matrix(latLongCoords)

    # we need a matrix of the form
    # x1 y1 1
    # x2 y2 1
    # ...
    # to allow for a constant offset
    onesColumn = np.ones((pointsLocal.shape[0], 1))
    pointsLocal = np.concatenate((pointsLocal, onesColumn), axis=1)

    # calculate pseudoinverse, this is the matrix
    # such that (x,y,1)*pInv ~= (lat, long).
    pInv = spl.pinv(pointsLocal)

    return pInv * pointsLatLong


def getPaths(transform=None):

    pathLayer = root.find("svg:g[@inkscape:label='Trace']", inkscapeNamespace)
    rawPaths = pathLayer.findall("svg:path", inkscapeNamespace)
    paths = []

    for path in rawPaths:
        nodesSplit = path.get("d").split(" ")
        if nodesSplit[0] != "m":
            raise Exception("path %s did not start with an m" % path.get("id"))
        if nodesSplit[-1] not in ["z", "Z"]:
            raise Exception("path %s was not closed properly" % path.get("id"))
        nodesSplit = nodesSplit[1:-1]
        currentAbsolutePosition = Vector(0, 0)
        nodes = []
        for nodeString in nodesSplit:
            node = tuple(map(float, nodeString.split(',')))
            currentAbsolutePosition += node
            nodeVector = currentAbsolutePosition.copy()
            nodeVector.y = nodeVector.y
            if transform is not None:
                nodeVector = nodeVector*transform
            nodes.append(nodeVector)
        names = None
        namesAttr = path.get("x-names")
        if namesAttr:
            names = namesAttr

        paths.append((nodes, names))

    return paths


def outputPaths(paths):
    nodeString = '\n'.join(
    				(';'.join((node.strNoBrackets() for node in path[0]))
				   +('~'+path[1] if path[1] else '')
                 for path in paths))
    return nodeString

print(outputPaths(getPaths(calculateTransform())))
