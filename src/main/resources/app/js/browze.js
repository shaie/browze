(function () {
  'use strict';

  /* Returns a node's full path. */
  var getFullPath = function (node) {
    var parent = node.parent == null ? '' : node.parent;
    var separator = parent == '/' || parent == '' ? '' : '/';
    return parent + separator + node.label;
  };

  /* Returns the index of the node whose label == label, or -1 if label is not found. */
  var findNodeInChildren = function(label, children) {
    if (children != null) {
      for (var i = 0; i < children.length; i++) {
        if (children[i].label == label) {
          return i;
        }
      }
    }

    // Either children == null, or children is empty, or label not found.
    return -1;
  };

  /* Extracts the first path component from a path. */
  var extractLabel = function (path) {
    if (path == null || path.length == 0) {
      return null;
    }
    var idx = path.indexOf('/');
    return idx == -1 ? path : path.substring(0, idx);
  };
  
  /* Splits a string on a character and removes any trailing and leading blanks in the result array. */
  var splitAndTrim = function (string, splitChar) {
    if (string == null || string.length == 0) {
      return [];
    }
    var elements = string.split(splitChar);
    var start = 0;
    for (var i = 0; i < elements.length; i++) {
      if (elements[i] == "") {
        ++start;
      } else {
        break;
      }
    }
    var end = elements.length;
    for (var i = elements.length - 1; i >= 0; i--) {
      if (elements[i] == "") {
        --end;
      } else {
        break;
      }
    }

    return elements.slice(start, end);
  }
  
  /* Normalizes a URL by replacing all sequences of consecutive '/' with one slash and removing trailing '/'. */
  var normalizeUrl = function (url) {
    return url != null ? url.replace(/\/+/g, "/").replace(/\/+$/g,"") : "";
  }

  var browzeApp = angular.module("browzeApp", ['treeControl', 'browzeServices']);

  browzeApp.filter('pathLabel', function () {
    return function (input) {
      return input == "/" ? "{root}" : input;
    };
  });

  browzeApp.controller('BrowzeController', ['$scope', '$location', 'Browze', function ($scope, $location, Browze) {

    /* Options for controlling tree settings. */
    $scope.treeOptions = {
        nodeChildren: "children",
        dirSelectable: false,
        isLeaf: function (node) {
          return node.leaf;
        },
        equality: function (node1, node2) {
          return node1 === node2;
        }
    };

    $scope.selectedPath = [ "/" ];
    $scope.treedata = [];
    $scope.selectedNode = null;
    $scope.expandedNodes = [ ];
    $scope.selectedNodeData = null;
    $scope.selectedNodeStat = null;
    $scope.errorMsg = null;

    /* Register a listener for browser's path changes. */
    $scope.$on("$locationChangeSuccess", function (event, newUrl) {
      if ($location.path().length == 0) {
        $location.path('/');
        event.preventDefault();
        return;
      }
      
      var path = $location.path(normalizeUrl($location.path())).path();
      
      // Remove all sub-nodes that are expanded.
      removeSubExpandedNodes(path);
      
      var fullHierarchyExists = path == '/' ? false : isFullHierarchyExists(path);
      
      Browze.get({path : path, full_hierarchy : !fullHierarchyExists}, function (success) {
        if (!fullHierarchyExists) {
          $scope.treedata[0] = success.tree;
          removeRootFromExpandedNodes();
        }
        
        var treeNode = findNode(path);
        if (fullHierarchyExists) {
          treeNode.children = success.tree.children;
          treeNode.leaf = success.tree.leaf;
        }
        
        if (treeNode.leaf) {
          $scope.selectedNode = treeNode;
        }
        
        setSelectedPath(path);
        $scope.selectedNodeData = success.data;
        $scope.selectedNodeStat = success.stat;
        expandAllNodesOnPath(treeNode.leaf ? treeNode.parent : path);
        $scope.errorMsg = null;
      }, function (error) {
        $scope.errorMsg = error.data;
      });
      
    });

    var isFullHierarchyExists = function (path){
      if ($scope.treedata.length == 0) {
        return false; // root element not obtained yet.
      }
      
      return findNode(path.substring(0, path.lastIndexOf('/'))) != null;
    };

    var addToExpandedNodesIfNotExist = function (node) {
      if (node == null) {
        return;
      }
      
      for (var i = 0; i < $scope.expandedNodes.length; i++) {
        if ($scope.expandedNodes[i] && $scope.expandedNodes[i].parent == node.parent && $scope.expandedNodes[i].label == node.label) {
          return;
        }
      }
      
      $scope.expandedNodes.push(node);
    }

    // Remove all child nodes from expandedNodes.
    var removeSubExpandedNodes = function (pathPrefix) {
      var i = 0;
      while (i < $scope.expandedNodes.length) {
        if ($scope.expandedNodes[i].parent && $scope.expandedNodes[i].parent.indexOf(pathPrefix) == 0) {
          $scope.expandedNodes.splice(i, 1); // remove the node
        } else {
          i++;
        }
      }
    };
    
    // Remove all child nodes from expandedNodes.
    var removeRootFromExpandedNodes = function () {
      var i = 0;
      while (i < $scope.expandedNodes.length) {
        if ($scope.expandedNodes[i].parent == null) {
          $scope.expandedNodes.splice(i, 1); // remove the node
        } else {
          i++;
        }
      }
    };

    /* Expands all nodes on the given path. */
    var expandAllNodesOnPath = function (path) {
      // ensure root is expanded
      addToExpandedNodesIfNotExist($scope.treedata[0]);
      
      var pathElements = splitAndTrim(path, '/');
      var node = $scope.treedata[0];
      for (var i = 0; i < pathElements.length; i++) {
        node = node.children[findNodeInChildren(pathElements[i], node.children)];
        addToExpandedNodesIfNotExist(node);
      }
    };

    var findNode = function(nodePath) {
      var pathElements = splitAndTrim(nodePath, '/');
      var node = $scope.treedata[0];
      for (var i = 0; i < pathElements.length; i++) {
        var nodeIdx = findNodeInChildren(pathElements[i], node.children);
        if (nodeIdx == -1) {
          return null;
        }
        node = node.children[nodeIdx];
      }
      return node;
    };

    // for debugging purposes
    var logExpandedNodes = function() {
      var labels = [];
      if ($scope.expandedNodes) {
        for (var i = 0; i < $scope.expandedNodes.length && $scope.expandedNodes[i] != null; i++) {
          var fullNodePath = getFullPath($scope.expandedNodes[i]);
          labels.push(fullNodePath);
        }
      }
      console.log(labels);
    };

    var setSelectedPath = function (path) {
      var split = splitAndTrim(path, '/');
      split.unshift("/");
      $scope.selectedPath = split;
    };
    
    $scope.showSelected = function (node, $parentNode, selected) {
      if (selected) {
        $location.path(getFullPath(node));
        $scope.selectedNode = node;
      } else {
        $location.path(node.parent);
        $scope.selectedNode = null;
      }
    };

    $scope.showToggle = function (node, expanded) {
      if (expanded && node.parent != null) {
        $location.path(getFullPath(node));
      }
    };

    $scope.selectedFullPath = function (nodeIdx) {
      return "/" + $scope.selectedPath.slice(1, nodeIdx + 1).join('/');
    };

  }]);
})();