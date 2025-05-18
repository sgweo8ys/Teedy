// filepath: d:\github\Teedy\docs-web\src\main\webapp\src\app\docs\controller\ModalRegister.js
'use strict';

angular.module('docs').controller('ModalRegister', function($scope, $uibModalInstance) {
  $scope.newUser = {};

  $scope.ok = function() {
    // 返回用户输入的信息到调用者
    $uibModalInstance.close($scope.newUser);
  };

  $scope.cancel = function() {
    $uibModalInstance.dismiss('cancel');
  };
});