(function () {
  'use strict';

  var browzeServices = angular.module('browzeServices', ['ngResource']);

  browzeServices.factory('Browze', ['$resource',
    function ($resource) {
      return $resource('api/zoo/:path', {}, {});
    }]);
})();