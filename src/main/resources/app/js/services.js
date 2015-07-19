(function () {
  'use strict';

  var browzeServices = angular.module('browzeServices', ['ngResource']);

  browzeServices.factory('Browze', ['$resource',
    function ($resource) {
      return $resource('api/zoo/browse/:path', {}, {
        'connect' : { method: 'GET', url: 'api/zoo/connect/:connectString' },
        'status' : { method: 'GET', url: 'api/zoo/status' }
      });
    }]);

})();