/// <reference path="../../typings/tsd.d.ts" />

var flowchart : any;

module app.query {
	import TestEditorController = app.dialogs.TestEditorController;
	import IModalService = angular.ui.bootstrap.IModalService;
	import IModalSettings = angular.ui.bootstrap.IModalSettings;
	import Test = app.models.Test;
	import ILibraryService = app.core.ILibraryService;
	import LibraryItem = app.models.LibraryItem;
	import Query = app.models.Query;
	import StartingRules = app.models.StartingRules;

	'use strict';

	class QueryController {
	}

	angular
		.module('app.query')
		.directive('clearQuery', function () {
			return {
				template: '<div>' +
				'<div class="modal-dialog">' +
				'<div class="modal-content">' +
				'<div class="modal-header">' +
				'<button type="button" class="close" ng-click="toggleClearQuery()" aria-hidden="true">&times;</button>' +
				'<h4 class="modal-title">{{ title }}</h4>' +
				'</div>' +
				'<div class="modal-body" ng-transclude></div>' +
				'</div>' +
				'</div>' +
				'</div>',
				restrict: 'E',
				transclude: true,
				replace:true,
				scope:true,
				link: function postLink(scope: any, element: any, attrs: any) {
					scope.title = attrs.title;
				}
			};
		})
		.controller('QueryController', ['LoggerService', '$scope', '$stateParams', '$uibModal','$window','LibraryService','AdminService',
			function QueryController (logger : app.blocks.ILoggerService, $scope : any, $stateParams : any, $modal : IModalService, $window : any,
									  libraryService : ILibraryService, adminService : IAdminService) {

				$scope.queryName = "";
				$scope.queryDescription = "";

				$scope.nextRuleID = 1;

				$scope.queryNameChange = function () {
					$scope.chartViewModel.data.name = $scope.queryName;
				};

				$scope.queryDescriptionChange = function () {
					$scope.chartViewModel.data.description = $scope.queryDescription;
				};

				$scope.ruleDescriptionChange = function () {
					var selectedRule = $scope.chartViewModel.getSelectedRule();
					selectedRule.data.description = $scope.ruleDescription;
				};

				$scope.rulePassActionChange = function () {
					var selectedRule = $scope.chartViewModel.getSelectedRule();
					selectedRule.data.onPass.action = $scope.rulePassAction;
					if ($scope.rulePassAction!="GOTO_RULES") {
						selectedRule.data.onPass.ruleId = <any>[];
					}
				};

				$scope.ruleFailActionChange = function () {
					var selectedRule = $scope.chartViewModel.getSelectedRule();
					selectedRule.data.onFail.action = $scope.ruleFailAction;
					if ($scope.ruleFailAction!="GOTO_RULES") {
						selectedRule.data.onFail.ruleId = <any>[];
					}
				};

				$scope.results = ['','GOTO_RULES','INCLUDE','EXCLUDE'];

				$scope.$on('editTest', function(event : any, ruleId : any) {
					if (ruleId!="0") {
						$scope.ruleId = ruleId;

						var selectedRule = $scope.chartViewModel.getSelectedRule();

						var test : Test = selectedRule.data.test;

						TestEditorController.open($modal, test)
							.result.then(function(resultData : Test){

							selectedRule.data.test = resultData;
						});
					}
				});

				$scope.$on('ruleDescription', function(event : any, description : any) {
					$scope.ruleDescription = description;
				});

				$scope.$on('rulePassAction', function(event : any, action : any) {
					$scope.rulePassAction = action;
				});

				$scope.$on('ruleFailAction', function(event : any, action : any) {
					$scope.ruleFailAction = action;
				});

				$scope.dataModel = false;
				$scope.ShowDataModel = function () {
					$scope.dataModel = !$scope.dataModel;
				};

				$scope.showClearQuery = false;
				$scope.toggleClearQuery = function () {
					$scope.showClearQuery = !$scope.showClearQuery;
				};

				$scope.clearQueryYes = function () {
					$scope.chartViewModel.clearQuery();
					$scope.nextRuleID = 0;
					$scope.ruleDescription = "";
					$scope.rulePassAction = "";
					$scope.ruleFailAction = "";
					$scope.nextRuleID = 1;
					this.toggleClearQuery();
				};

				//
				// Add a new rule to the chart.
				//
				$scope.addNewRule = function () {
					//
					// Template for a new rule.
					//

					if ($scope.nextRuleID==1) {

						var newStartRuleDataModel = {
							description: "START",
							id: 0,
							layout: {
								x: -162,
								y: 25
							},
							onPass: {
								action: "",
								ruleId : <any>[]
							},
							onFail: {
								action: "",
								ruleId: <any>[]
							}
						};

						$scope.chartViewModel.addRule(newStartRuleDataModel);

						var newRuleDataModel = {
							description: "Rule Description",
							id: $scope.nextRuleID++,
							layout: {
								x: 194,
								y: 5
							},
							onPass: {
								action: "",
								ruleId: <any>[]
							},
							onFail: {
								action: "",
								ruleId: <any>[]
							}
						};

						$scope.chartViewModel.addRule(newRuleDataModel);

						$scope.chartViewModel.addStartingRule(1);
					}
					else {
						var newRuleDataModel = {
							description: "Rule Description",
							id: $scope.nextRuleID++,
							layout: {
								x: 100,
								y: 10
							},
							onPass: {
								action: "",
								ruleId: <any>[]
							},
							onFail: {
								action: "",
								ruleId: <any>[]
							}
						};

						$scope.chartViewModel.addRule(newRuleDataModel);
					}
				};

				//
				// Delete selected rule and connections.
				//
				$scope.deleteSelected = function () {
					$scope.chartViewModel.deleteSelected();
				};

				$scope.save = function (close : boolean) {
					for (var i = 0; i < $scope.chartViewModel.data.query.rule.length; ++i) {
						if ($scope.chartViewModel.data.query.rule[i].description=="START") {
							$scope.chartViewModel.data.query.rule.splice(i,1);
							$scope.chartViewModel.rule.splice(i,1);
						}

					}

					var libraryItem = $scope.chartViewModel.data;

					libraryService.saveLibraryItem(libraryItem)
						.then(function(libraryItem : LibraryItem) {
							$scope.chartViewModel.data.uuid = libraryItem.uuid;

							var newStartRuleDataModel = {
								description: "START",
								id: 0,
								layout: {
									x: -162,
									y: 25
								},
								onPass: {
									action: "",
									ruleId : <any>[]
								},
								onFail: {
									action: "",
									ruleId: <any>[]
								}
							};

							$scope.chartViewModel.addRule(newStartRuleDataModel);

							adminService.clearPendingChanges();
							logger.success('Query saved successfully', libraryItem, 'Saved');

							if (close) { $window.history.back(); }
						})
						.catch(function(data) {
							logger.error('Error saving query', data, 'Error');
						});;
				}

				$scope.close = function () {
					// put code in here to check for changes
					adminService.clearPendingChanges();
					logger.error('Query not saved');
					$window.history.back();
				}

				//
				// Setup the data-model for the chart.
				//

				var startingRules : StartingRules = {
					ruleId : []
				}

				var query : Query = {
					parentQueryUuid  : null,
					startingRules : startingRules,
					rule : []
				}

				var libraryItem : LibraryItem = {
					uuid: null,
					name: null,
					description: null,
					folderUuid: $stateParams.itemUuid,
					query: query,
					codeSet : null,
					listReport : null
				};

				//
				// Create the view-model for the chart and attach to the scope.
				//
				$scope.chartViewModel = new flowchart.ChartViewModel(libraryItem);

				switch($stateParams.itemAction) {
					case "view":
					case "edit":
						$stateParams.itemUuid = "A87A233D-293E-4220-AAA3-25AC1D201257";
						libraryService.getLibraryItem($stateParams.itemUuid)
							.then(function(libraryItem : LibraryItem) {
								$scope.chartViewModel = new flowchart.ChartViewModel(libraryItem);

								$scope.queryName = libraryItem.name;
								$scope.queryDescription = libraryItem.description;


								var newStartRuleDataModel = {
									description: "START",
									id: 0,
									layout: {
										x: -162,
										y: 25
									},
									onPass: {
										action: "",
										ruleId : <any>[]
									},
									onFail: {
										action: "",
										ruleId: <any>[]
									}
								};

								$scope.chartViewModel.addRule(newStartRuleDataModel);

								var highestId = 1;
								for (var i = 0; i < $scope.chartViewModel.data.query.rule.length; ++i) {
									var id = $scope.chartViewModel.data.query.rule[i].id;
									if (parseInt(id) > highestId) {
										highestId = parseInt(id);
									}
								}
								$scope.nextRuleID = highestId+1;

							})
							.catch(function(data) {
								logger.error('Error loading query', data, 'Error');
							});;
						break;
					default:
				}


			}])

}