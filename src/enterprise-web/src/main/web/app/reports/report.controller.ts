/// <reference path="../../typings/tsd.d.ts" />
/// <reference path="../core/library.service.ts" />

module app.reports {
	import FolderNode = app.models.FolderNode;
	import ItemSummaryList = app.models.ItemSummaryList;
	import ILoggerService = app.blocks.ILoggerService;
	import FolderContent = app.models.FolderItem;
	import itemTypeIdToString = app.models.itemTypeIdToString;
	import LibraryController = app.library.LibraryController;
	import ICallbacks = AngularUITree.ICallbacks;
	import IEventInfo = AngularUITree.IEventInfo;
	import FolderItem = app.models.FolderItem;
	import ReportNode = app.models.ReportNode;
	import ItemType = app.models.ItemType;
	import Report = app.models.Report;
	import Query = app.models.Query;
	import ListOutput = app.models.ListOutput;
	import UuidNameKVP = app.models.UuidNameKVP;
	import IScope = angular.IScope;
	'use strict';

	class ReportController {
		treeData : FolderNode[];
		selectedNode : FolderNode = null;
		itemSummaryList : ItemSummaryList;
		report : Report;
		reportContent : ReportNode[];
		contentTreeCallbackOptions : ICallbacks;

		static $inject = ['LibraryService', 'LoggerService', '$stateParams'];

		constructor(
			protected libraryService:app.core.ILibraryService,
			protected logger : ILoggerService,
			protected $stateParams : {itemAction : string, itemUuid : string}) {
			this.contentTreeCallbackOptions = {dropped: this.contentTreeDroppedCallback, accept: null, dragStart: null};

			this.getLibraryRootFolders();
			this.performAction($stateParams.itemAction, $stateParams.itemUuid);
		}

		// General report methods
		performAction(action:string, itemUuid:string) {
			switch (action) {
				case 'add':
					this.createReport(itemUuid);
					break;
				case 'view':
					this.getReport(itemUuid);
					break;
			}
		}

		createReport(folderUuid:string) {
			// Initialize blank report
			this.report = {
				uuid: '',
				name: 'New report',
				description: '',
				folderUuid: folderUuid,
				query: [],
				listOutput: []
			};
			this.reportContent = [];
		}

		getReport(reportUuid:string) {
			var vm = this;
			vm.libraryService.getReport(reportUuid)
				.then(function (data) {
					vm.report = data;
					vm.reportContent = [];
					vm.libraryService.getLibraryItemNamesForReport(reportUuid)
						.then(function(data) {
						vm.populateTreeFromReportLists(vm.report, vm.reportContent, '', data);
					})
					.catch(function(data) {
						vm.logger.error('Error loading report item names', data, 'Error');
					});
				})
				.catch(function(data) {
					vm.logger.error('Error loading report', data, 'Error');
				});
		}

		populateTreeFromReportLists(report : Report,
																nodeList : ReportNode[],
																parentUuid : string,
																nameMap : UuidNameKVP[]) {
			if (report.query == null) { report.query = []; }
			for (var i = 0; i < report.query.length; i++) {
				if (this.report.query[i].parentUuid === parentUuid) {
					var reportNode:ReportNode = {
						name : $.grep(nameMap,
							(e : UuidNameKVP) => { return e.uuid === report.query[i].uuid; }
						)[0].name,
						uuid : report.query[i].uuid,
						type : ItemType.Query,
						children : []
					};
					nodeList.push(reportNode);
					this.populateTreeFromReportLists(report, reportNode.children, reportNode.uuid, nameMap);
				}
			}
			if (report.listOutput == null) { report.listOutput = []; }
			for (var i = 0; i < report.listOutput.length; i++) {
				if (this.report.listOutput[i].parentUuid === parentUuid) {
					var reportNode:ReportNode = {
						name : $.grep(nameMap,
							(e : UuidNameKVP) => { return e.uuid === report.query[i].uuid; }
						)[0].name,
						uuid : report.listOutput[i].uuid,
						type : ItemType.ListOutput,
						children : []
					};
					nodeList.push(reportNode);
					this.populateTreeFromReportLists(report, reportNode.children, reportNode.uuid, nameMap);
				}
			}
		}

		saveReport() {
			var vm = this;
			vm.report.query = [];
			vm.report.listOutput = [];
			vm.populateReportListsFromTree(vm.report, '', vm.reportContent);

			vm.libraryService.saveReport(vm.report)
				.then(function (data:Report) {
					vm.report.uuid = data.uuid;
					vm.logger.success('Report saved', vm.report, 'Saved');
				})
				.catch(function(data) {
					vm.logger.error('Error saving report', data, 'Error');
				});
		}

		populateReportListsFromTree(report : Report, parentUuid : string, nodes : ReportNode[]) {
			for (var i = 0; i < nodes.length; i++) {
				switch (nodes[i].type) {
					case ItemType.Query:
						var query : Query = <Query>{};
						query.uuid = nodes[i].uuid;
						query.parentUuid = parentUuid;
						report.query.push(query);
						break;
					case ItemType.ListOutput:
						var listOutput : ListOutput = <ListOutput>{};
						listOutput.uuid = nodes[i].uuid;
						listOutput.parentUuid = parentUuid;
						report.listOutput.push(listOutput);
						break;
				}
				if (nodes[i].children && nodes[i].children.length > 0) {
					this.populateReportListsFromTree(report, nodes[i].uuid, nodes[i].children);
				}
			}
		}

		// Library tree methods
		getLibraryRootFolders() {
			var vm = this;
			vm.libraryService.getFolders(1, null)
				.then(function (data) {
					vm.treeData = data;
				});
		}

		selectNode(node : FolderNode) {
			if (node === this.selectedNode) { return; }
			var vm = this;

			if (vm.selectedNode !== null) {
				vm.selectedNode.isSelected = false;
			}
			node.isSelected = true;
			vm.selectedNode = node;
			node.loading = true;
			vm.libraryService.getFolderContents(node.uuid)
				.then(function(data) {
					vm.itemSummaryList = data;
					// filter content by those allowed in reports
					vm.itemSummaryList.contents = vm.itemSummaryList.contents.filter(vm.validReportItemType);
					node.loading = false;
				});
		}

		toggleExpansion(node : FolderNode) {
			if (!node.hasChildren) { return; }

			node.isExpanded = !node.isExpanded;

			if (node.isExpanded && (node.nodes == null || node.nodes.length === 0)) {
				var vm = this;
				var folderId = node.uuid;
				node.loading = true;
				this.libraryService.getFolders(1, folderId)
					.then(function (data) {
						node.nodes = data;
						node.loading = false;
					});
			}
		}

		// Report structure methods
		remove(scope:any) {
			scope.remove();
		}

		// Library folder content methods
		validReportItemType(input:FolderContent):boolean {
			switch (input.type) {
				case ItemType.Query:
				case ItemType.ListOutput:
					return true;
				default:
					return false;
			}
		};

		contentTreeDroppedCallback(eventInfo: IEventInfo) {
			// Convert clone model to report node
			eventInfo.source.cloneModel.children = [];
		}

	}

	angular
		.module('app.reports')
		.controller('ReportController', ReportController);
}
