export class RowLimiter {

  pageSizes: Array<any> = [
    {key: '10', value: 10},
    {key: '25', value: 25},
    {key: '50', value: 50},
    {key: '100', value: 100},
    {key: '500', value: 500}
  ];

  pageSize: number = this.pageSizes[2].value;
}
