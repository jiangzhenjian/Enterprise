/// <reference path="../../typings/tsd.d.ts" />
/// <reference path="../blocks/logger.service.ts" />

module app.login {
	import IIdleService = angular.idle.IIdleService;
	import IAdminService = app.core.IAdminService;
	import AdminService = app.core.AdminService;
	import IStateService = angular.ui.IStateService;
	'use strict';

	export class LoginController {
		username:string;
		password:string;

		static $inject = ['LoggerService', 'Idle', '$state', 'AdminService'];

		constructor(private logger:app.blocks.ILoggerService,
								private Idle:IIdleService,
								private $state : IStateService,
								private AdminService:IAdminService) {
			Idle.unwatch();
			AdminService.logout();
		}

		login() {
			var vm = this;
			vm.AdminService.login(vm.username, vm.password)
				.then(function (response) {
					vm.logger.success('User logged in', vm.username, 'Logged In');
					vm.Idle.watch();
					vm.$state.transitionTo('app.dashboard');
				})
				.catch(function (data) {
					vm.logger.error(data.statusText, data, 'Login error!');
				});
		}
	}

	angular
		.module('app.login')
		.controller('LoginController', LoginController);
}
